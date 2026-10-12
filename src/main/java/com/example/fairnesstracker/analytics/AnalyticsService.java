package com.example.fairnesstracker.analytics;

import com.example.fairnesstracker.analytics.AnalyticsRepository.KpiRow;
import com.example.fairnesstracker.analytics.AnalyticsRepository.Slice;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.AnalyticsFilter;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Heatmap;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.HeatmapCell;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Kpi;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.ScoresStatus;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.SeriesPoint;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.ServiceStats;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.SeverityCounts;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Snapshot;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Summary;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.TimeSeries;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.TopServices;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Trend;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Window;
import com.example.fairnesstracker.common.exception.ScoringServiceException;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.scoring.ScoringClient;
import com.example.fairnesstracker.scoring.dto.ScoreQuery;
import com.example.fairnesstracker.scoring.dto.Scores;
import com.example.fairnesstracker.security.AccessPolicy;
import com.example.fairnesstracker.security.DataScope;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the analytics page. Counts and averages come from SQL (always available); Gini, burnout and trends
 * from the scorer, and when it can't answer the rest is still returned with {@link ScoresStatus} saying why.
 * Every figure covers what the caller may see: an admin everything, a manager their teams, an engineer
 * their own alerts (and their team's fairness, with only their own row).
 *
 * <p>Deliberately not transactional: no database connection is held while the scorer is called.
 */
@Slf4j
@Service
public class AnalyticsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 3650;
    static final int MAX_DAILY_POINTS = 400;
    static final int TOP_SERVICES = 10;
    // The scorer allows 120 periods: longer windows are trended by month
    static final int MAX_WEEKLY_TREND_DAYS = 7 * 110;

    private final AnalyticsRepository repository;
    private final ScoringClient scoring;
    private final AccessPolicy access;
    private final TeamRepository teams;
    private final ZoneId zone;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, ScoringClient scoring, AccessPolicy access,
                            TeamRepository teams, AnalyticsProperties properties, Clock clock) {
        this.repository = repository;
        this.scoring = scoring;
        this.access = access;
        this.teams = teams;
        this.zone = properties.zone();
        this.clock = clock;
    }

    public Summary summary(AnalyticsFilter filter) {
        Request request = resolve(filter);
        return summary(request, scores(request));
    }

    public TimeSeries alertsOverTime(AnalyticsFilter filter) {
        return alertsOverTime(resolve(filter));
    }

    public Heatmap heatmap(AnalyticsFilter filter) {
        return heatmap(resolve(filter));
    }

    public TopServices topServices(AnalyticsFilter filter) {
        return topServices(resolve(filter));
    }

    public Trend trend(AnalyticsFilter filter) {
        return trend(resolve(filter));
    }

    /** Everything at once, with one call per scorer figure: what the page and the reports use. */
    public Snapshot snapshot(AnalyticsFilter filter) {
        Request request = resolve(filter);
        ScorerFigures scores = scores(request);
        return new Snapshot(summary(request, scores), alertsOverTime(request), heatmap(request),
                topServices(request), trend(request),
                scores.current() != null ? scores.current().engineers() : List.of(),
                scores.burnout() != null ? scores.burnout().engineers() : List.of());
    }

    // --- The parts -----------------------------------------------------------------------------------

    private Summary summary(Request request, ScorerFigures scores) {
        KpiRow now = repository.kpis(request.current());
        KpiRow before = repository.kpis(request.previous());
        Kpi gini = null;
        String label = null;
        Kpi highRisk = null;
        if (scores.status().available()) {
            gini = giniKpi(scores.current().gini(), scores.previous().gini());
            label = scores.current().label();
            if (!request.engineerOnly()) {
                highRisk = kpi((double) highRisk(scores.burnout()), (double) highRisk(scores.burnoutBefore()));
            }
        }
        return new Summary(request.window(), request.previousWindow(), request.teamName(),
                kpi((double) now.alerts(), (double) before.alerts()),
                kpi(now.mttaMinutes(), before.mttaMinutes()),
                kpi(now.mttrMinutes(), before.mttrMinutes()),
                kpi(now.afterHoursPct(), before.afterHoursPct()),
                kpi(now.escalationRatePct(), before.escalationRatePct()),
                gini, label, highRisk, scores.status());
    }

    private TimeSeries alertsOverTime(Request request) {
        Map<LocalDate, int[]> counts = new HashMap<>();
        for (AnalyticsRepository.SeverityBucket row : repository.countsBySeverity(request.current(),
                request.granularity(), zone.getId())) {
            int[] bySeverity = counts.computeIfAbsent(row.bucket(), day -> new int[6]);
            bySeverity[severityIndex(row.severity())] += row.count();
        }
        // Every period in the window, quiet ones included, so charts have no gaps
        List<SeriesPoint> points = new ArrayList<>();
        for (LocalDate start = periodStart(request.window().start(), request.granularity());
             !start.isAfter(request.window().end()); start = nextPeriod(start, request.granularity())) {
            int[] c = counts.getOrDefault(start, new int[6]);
            points.add(new SeriesPoint(start, c[0] + c[1] + c[2] + c[3] + c[4] + c[5],
                    new SeverityCounts(c[0], c[1], c[2], c[3], c[4], c[5])));
        }
        return new TimeSeries(request.window(), request.teamName(), request.granularity(), points);
    }

    private Heatmap heatmap(Request request) {
        int[][] grid = new int[7][24];
        repository.countsByWeekdayAndHour(request.current())
                .forEach(cell -> grid[cell.dayOfWeek() - 1][cell.hour()] += cell.count());
        List<HeatmapCell> cells = new ArrayList<>(7 * 24);
        int max = 0;
        for (int day = 1; day <= 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                int count = grid[day - 1][hour];
                cells.add(new HeatmapCell(day, hour, count));
                max = Math.max(max, count);
            }
        }
        return new Heatmap(request.window(), request.teamName(), max, cells);
    }

    private TopServices topServices(Request request) {
        List<ServiceStats> services = repository.noisiestServices(request.current(), TOP_SERVICES).stream()
                .map(row -> new ServiceStats(row.serviceId(), row.name(), row.alertCount(), round(row.mttrMinutes()),
                        round(row.afterHoursPct())))
                .toList();
        return new TopServices(request.window(), request.teamName(), services);
    }

    private Trend trend(Request request) {
        String granularity = "month".equals(request.granularity()) || request.days() > MAX_WEEKLY_TREND_DAYS
                ? "month" : "week";
        if (request.scorerBlocked() != null) {
            return new Trend(request.window(), request.teamName(), granularity, List.of(), request.scorerBlocked());
        }
        try {
            Scores.TrendReport report = scoring.trend(request.scoreQuery(), granularity);
            return new Trend(request.window(), request.teamName(), granularity, report.periods(),
                    ScoresStatus.AVAILABLE);
        } catch (ScoringServiceException e) {
            log.warn("Trend unavailable: {}", e.getMessage());
            return new Trend(request.window(), request.teamName(), granularity, List.of(), unavailable());
        }
    }

    // --- Scorer figures ------------------------------------------------------------------------------

    private record ScorerFigures(Scores.FairnessReport current, Scores.FairnessReport previous,
                                 Scores.BurnoutReport burnout, Scores.BurnoutReport burnoutBefore,
                                 ScoresStatus status) {
    }

    private ScorerFigures scores(Request request) {
        if (request.scorerBlocked() != null) {
            return new ScorerFigures(null, null, null, null, request.scorerBlocked());
        }
        try {
            Scores.FairnessReport current = scoring.fairness(request.scoreQuery());
            Scores.FairnessReport previous = scoring.fairness(request.previousScoreQuery());
            Scores.BurnoutReport burnout = scoring.burnout(request.scoreQuery());
            Scores.BurnoutReport burnoutBefore = scoring.burnout(request.previousScoreQuery());
            Long own = access.ownEngineerOnly();
            if (own != null) {
                // An engineer sees their team's figures but only their own row
                current = current.onlyEngineer(own);
                burnout = new Scores.BurnoutReport(burnout.window(), burnout.team(),
                        burnout.engineers().stream().filter(b -> b.engineerId() == own).toList());
            }
            return new ScorerFigures(current, previous, burnout, burnoutBefore, ScoresStatus.AVAILABLE);
        } catch (ScoringServiceException e) {
            log.warn("Scores unavailable for analytics: {}", e.getMessage());
            return new ScorerFigures(null, null, null, null, unavailable());
        }
    }

    private static int highRisk(Scores.BurnoutReport report) {
        return (int) report.engineers().stream().filter(b -> "HIGH".equals(b.risk())).count();
    }

    private static ScoresStatus unavailable() {
        return new ScoresStatus(false, "SCORER_UNAVAILABLE");
    }

    // --- The request ---------------------------------------------------------------------------------

    /**
     * A filter resolved against the caller: the window and the one before it (UTC slices for SQL, dates for
     * the scorer), the team, and the team the scorer should score (or why it can't).
     */
    private record Request(Window window, Window previousWindow, long days, String granularity, Slice current,
                           Slice previous, String teamName, String scorerTeam, ScoresStatus scorerBlocked,
                           boolean engineerOnly) {

        ScoreQuery scoreQuery() {
            return new ScoreQuery(null, window.start(), window.end(), scorerTeam);
        }

        ScoreQuery previousScoreQuery() {
            return new ScoreQuery(null, previousWindow.start(), previousWindow.end(), scorerTeam);
        }
    }

    private Request resolve(AnalyticsFilter filter) {
        LocalDate end = filter.end() != null ? filter.end() : LocalDate.now(clock.withZone(zone));
        LocalDate start = filter.start() != null ? filter.start() : end.minusDays(DEFAULT_DAYS - 1);
        if (end.isBefore(start)) {
            throw ValidationException.onField("end", "must be on or after start");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_DAYS) {
            throw ValidationException.onField("start", "the window can be at most " + MAX_DAYS + " days");
        }
        String granularity = filter.granularity() != null ? filter.granularity()
                : days <= 31 ? "day" : days <= 183 ? "week" : "month";
        if ("day".equals(granularity) && days > MAX_DAILY_POINTS) {
            throw ValidationException.onField("granularity", "use week or month for windows over "
                    + MAX_DAILY_POINTS + " days");
        }
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days - 1);

        DataScope scope = access.scope();
        Team team = null;
        if (filter.team() != null && !filter.team().isBlank()) {
            String name = filter.team().strip();
            team = teams.findByName(name).orElseThrow(() -> ValidationException.onField("team", "Unknown team '" + name + "'"));
            access.requireTeam(team.getId());
        }
        Long teamId = team != null ? team.getId() : null;

        // Gini and burnout are per team: score the chosen team, the only one in scope, or (admins) all of them
        String scorerTeam = null;
        ScoresStatus blocked = null;
        if (team != null) {
            scorerTeam = team.getName();
        } else if (scope.teamIds() != null && scope.teamIds().size() == 1) {
            scorerTeam = teams.findById(scope.teamIds().iterator().next()).map(Team::getName).orElse(null);
        } else if (!scope.isAll()) {
            blocked = new ScoresStatus(false, "SEVERAL_TEAMS");
        }

        Window window = new Window(start, end, zone.getId());
        return new Request(window, new Window(previousStart, previousEnd, zone.getId()), days, granularity,
                new Slice(utc(start), utc(end.plusDays(1)), teamId, scope),
                new Slice(utc(previousStart), utc(previousEnd.plusDays(1)), teamId, scope),
                team != null ? team.getName() : null, scorerTeam, blocked, scope.engineerId() != null);
    }

    private LocalDateTime utc(LocalDate day) {
        return day.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    // --- Helpers -------------------------------------------------------------------------------------

    private static LocalDate periodStart(LocalDate day, String granularity) {
        return switch (granularity) {
            case "week" -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> day.withDayOfMonth(1);
            default -> day;
        };
    }

    private static LocalDate nextPeriod(LocalDate start, String granularity) {
        return switch (granularity) {
            case "week" -> start.plusWeeks(1);
            case "month" -> start.plusMonths(1);
            default -> start.plusDays(1);
        };
    }

    // P1..P5, then anything else (including no severity)
    private static int severityIndex(String severity) {
        if (severity != null && severity.length() == 2 && severity.charAt(0) == 'P'
                && severity.charAt(1) >= '1' && severity.charAt(1) <= '5') {
            return severity.charAt(1) - '1';
        }
        return 5;
    }

    private static Kpi kpi(Double value, Double previous) {
        Double change = value == null || previous == null || previous == 0 ? null : round((value - previous) / previous * 100);
        return new Kpi(round(value), round(previous), change);
    }

    // Gini lies between 0 and 1: keep the scorer's three decimals
    private static Kpi giniKpi(double value, double previous) {
        Double change = previous == 0 ? null : round((value - previous) / previous * 100);
        return new Kpi(Math.round(value * 1000) / 1000.0, Math.round(previous * 1000) / 1000.0, change);
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
