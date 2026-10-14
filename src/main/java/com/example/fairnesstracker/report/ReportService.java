package com.example.fairnesstracker.report;

import com.example.fairnesstracker.alert.AlertService;
import com.example.fairnesstracker.alert.dto.AlertFilter;
import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.analytics.AnalyticsProperties;
import com.example.fairnesstracker.analytics.AnalyticsService;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.AnalyticsFilter;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Snapshot;
import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditService;
import com.example.fairnesstracker.common.exception.ResourceNotFoundException;
import com.example.fairnesstracker.common.exception.ScoringServiceException;
import com.example.fairnesstracker.common.exception.TooManyRequestsException;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.common.web.PageResponse;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.oncall.OncallService;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import com.example.fairnesstracker.report.ReportData.Header;
import com.example.fairnesstracker.report.ReportData.Rows;
import com.example.fairnesstracker.report.dto.ReportExportResponse;
import com.example.fairnesstracker.scoring.ScoringClient;
import com.example.fairnesstracker.scoring.dto.ScoreQuery;
import com.example.fairnesstracker.scoring.dto.Scores;
import com.example.fairnesstracker.security.AccessPolicy;
import com.example.fairnesstracker.security.CurrentUser;
import com.example.fairnesstracker.security.DataScope;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds PDF and Excel reports from the same figures as the analytics pages, for what the caller may see.
 * Every download is recorded in {@code report_export} (failures too, once the request is valid) and audited.
 *
 * <p>Not transactional: the export row is saved before the work and again after it, and no database
 * connection is held while the scorer answers or the file is rendered.
 */
@Slf4j
@Service
public class ReportService {

    static final int DEFAULT_DAYS = 30;
    static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(5);

    private final AnalyticsService analytics;
    private final ScoringClient scoring;
    private final AlertService alerts;
    private final OncallService oncall;
    private final AccessPolicy access;
    private final TeamRepository teams;
    private final EngineerRepository engineers;
    private final ReportExportRepository exports;
    private final AuditService audit;
    private final ReportProperties properties;
    private final ZoneId zone;
    private final Clock clock;
    private final Map<ReportExport.Format, ReportRenderer> renderers = new EnumMap<>(ReportExport.Format.class);
    // One permit per report being built (ReportProperties.maxConcurrent)
    private final Semaphore building;

    public ReportService(AnalyticsService analytics, ScoringClient scoring, AlertService alerts, OncallService oncall,
                         AccessPolicy access, TeamRepository teams, EngineerRepository engineers,
                         ReportExportRepository exports, AuditService audit, ReportProperties properties,
                         AnalyticsProperties analyticsProperties, List<ReportRenderer> renderers, Clock clock) {
        this.analytics = analytics;
        this.scoring = scoring;
        this.alerts = alerts;
        this.oncall = oncall;
        this.access = access;
        this.teams = teams;
        this.engineers = engineers;
        this.exports = exports;
        this.audit = audit;
        this.properties = properties;
        this.zone = analyticsProperties.zone();
        this.clock = clock;
        renderers.forEach(renderer -> this.renderers.put(renderer.format(), renderer));
        this.building = new Semaphore(properties.maxConcurrent());
    }

    /** The analytics page as a file: a team, or every team the caller may see. */
    public ReportFile analytics(@Nullable String teamName, @Nullable LocalDate start, @Nullable LocalDate end,
                                ReportExport.Format format) {
        Days days = window(start, end);
        Team team = team(teamName);
        String scope = team != null ? team.getName() : "All teams";
        String fileName = fileName("analytics", team != null ? team.getName() : "all-teams", days, format);
        return whenBuildable(() -> {
            ReportExport export = exports.save(new ReportExport(CurrentUser.id().orElse(null),
                    ReportExport.Type.ANALYTICS, format, team != null ? team.getId() : null, null, days.start(),
                    days.end(), fileName));
            return produce(export, () -> gatherAnalytics(team, scope, days), details(export, scope));
        });
    }

    /** One engineer's report: their burnout and its parts, time of day, alerts and shifts. */
    public ReportFile engineer(long engineerId, @Nullable LocalDate start, @Nullable LocalDate end,
                               ReportExport.Format format) {
        Days days = window(start, end);
        access.requireEngineer(engineerId);
        Engineer engineer = engineers.findById(engineerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Engineer", engineerId));
        String fileName = fileName("engineer", engineer.getName(), days, format);
        return whenBuildable(() -> {
            ReportExport export = exports.save(new ReportExport(CurrentUser.id().orElse(null),
                    ReportExport.Type.ENGINEER, format, engineers.findTeamIdById(engineerId).orElse(null), engineerId,
                    days.start(), days.end(), fileName));
            return produce(export, () -> gatherEngineer(engineer, days), details(export, engineer.getName()));
        });
    }

    /** The caller's own downloads, newest first, naming the team or engineer each covered. */
    public PageResponse<ReportExportResponse> history(PageRequest page) {
        Long userId = CurrentUser.id().orElseThrow();
        Page<ReportExport> rows = exports.findByUserId(userId,
                page.withSort(Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Long, String> teamNames = new HashMap<>();
        teams.findAllById(rows.stream().map(ReportExport::getTeamId).filter(Objects::nonNull).distinct().toList())
                .forEach(team -> teamNames.put(team.getId(), team.getName()));
        Map<Long, String> engineerNames = new HashMap<>();
        engineers.findAllById(rows.stream().map(ReportExport::getEngineerId).filter(Objects::nonNull).distinct().toList())
                .forEach(engineer -> engineerNames.put(engineer.getId(), engineer.getName()));
        return PageResponse.from(rows.map(row -> new ReportExportResponse(row.getId(), row.getType().name(),
                row.getFormat().name(), row.getType() == ReportExport.Type.ANALYTICS ? teamNames.get(row.getTeamId()) : null,
                row.getEngineerId(), engineerNames.get(row.getEngineerId()), row.getWindowStart(), row.getWindowEnd(),
                row.getStatus().name(), row.getFileName(), row.getSizeBytes(), row.getDurationMs(), row.getError(),
                row.getCreatedAt())));
    }

    // --- Gathering ------------------------------------------------------------------------------------

    private ReportData gatherAnalytics(@Nullable Team team, String scope, Days days) {
        String teamName = team != null ? team.getName() : null;
        Snapshot snapshot = analytics.snapshot(new AnalyticsFilter(teamName, days.start(), days.end(), null));
        Scores.TimeOfDayReport timeOfDay = snapshot.summary().scores().available() ? timeOfDay(teamName, days) : null;
        AlertFilter alertFilter = new AlertFilter(null, teamName, null, null, days.start(), days.end(), null);
        DataScope shiftScope = shiftScope(team);
        return new ReportData.Analytics(header("Fairness report", scope, days), snapshot, timeOfDay,
                alerts(alertFilter), shifts(days, null, shiftScope));
    }

    // Like the analytics report, it's still made when the scorer can't answer: without the scores
    private ReportData gatherEngineer(Engineer engineer, Days days) {
        long engineerId = engineer.getId();
        Scores.EngineerReport report;
        try {
            report = scoring.engineer(engineerId, new ScoreQuery(null, days.start(), days.end(), null));
        } catch (ScoringServiceException e) {
            log.warn("Scores unavailable for an engineer report: {}", e.getMessage());
            report = null;
        }
        Scores.EngineerRef who = new Scores.EngineerRef(engineerId, engineer.getName(), engineer.getTeam().getName());
        AlertFilter alertFilter = new AlertFilter(engineerId, null, null, null, days.start(), days.end(), null);
        return new ReportData.Engineer(header("Engineer report", who.name() + " · " + who.team(), days), who,
                engineer.isActive(), report, alerts(alertFilter), shifts(days, engineerId, access.scope()));
    }

    // Time of day isn't part of the snapshot; the same team, with an engineer seeing only their own row
    private Scores.@Nullable TimeOfDayReport timeOfDay(@Nullable String teamName, Days days) {
        try {
            Scores.TimeOfDayReport report = scoring.timeOfDay(
                    access.teamScoped(new ScoreQuery(null, days.start(), days.end(), teamName), true));
            Long own = access.ownEngineerOnly();
            return own == null ? report : report.onlyEngineer(own);
        } catch (ScoringServiceException e) {
            log.warn("Time of day unavailable for a report: {}", e.getMessage());
            return null;
        }
    }

    private Rows<AlertResponse> alerts(AlertFilter filter) {
        PageResponse<AlertResponse> page = alerts.list(filter,
                PageRequest.of(0, properties.maxAlertRows(), Sort.by(Sort.Direction.DESC, "triggeredAt")),
                access.scope());
        return new Rows<>(page.content(), page.totalElements());
    }

    // Shifts have no team filter of their own: narrow the caller's scope to the team
    private DataScope shiftScope(@Nullable Team team) {
        DataScope scope = access.scope();
        return team == null || scope.engineerId() != null ? scope : DataScope.teams(Set.of(team.getId()));
    }

    private Rows<OncallShiftResponse> shifts(Days days, @Nullable Long engineerId, DataScope scope) {
        PageResponse<OncallShiftResponse> page = oncall.shifts(days.start(), days.end(), engineerId,
                PageRequest.of(0, properties.maxShiftRows(), Sort.by(Sort.Direction.ASC, "startsAt")), scope);
        return new Rows<>(page.content(), page.totalElements());
    }

    private Header header(String title, String scope, Days days) {
        return new Header(title, scope, days.start(), days.end(), clock.instant(), CurrentUser.email().orElse(null),
                zone);
    }

    // --- Producing ------------------------------------------------------------------------------------

    // Refused before anything is recorded: a busy server isn't a failed export
    private ReportFile whenBuildable(Supplier<ReportFile> build) {
        if (!building.tryAcquire()) {
            throw new TooManyRequestsException("Other reports are being built right now; try again in a moment",
                    BUSY_RETRY_AFTER);
        }
        try {
            return build.get();
        } finally {
            building.release();
        }
    }

    private ReportFile produce(ReportExport export, Supplier<ReportData> gather, Map<String, Object> details) {
        long started = System.nanoTime();
        Function<Long, Integer> elapsed = from -> (int) ((System.nanoTime() - from) / 1_000_000);
        try {
            byte[] content = renderers.get(export.getFormat()).render(gather.get());
            export.succeeded(content.length, elapsed.apply(started));
            exports.save(export);
            audit.record(AuditAction.REPORT_EXPORTED, "REPORT_EXPORT", export.getId(), details);
            return new ReportFile(export.getFileName(), export.getFormat().contentType(), content);
        } catch (RuntimeException e) {
            export.failed(describe(e), elapsed.apply(started));
            exports.save(export);
            throw e;
        }
    }

    private static Map<String, Object> details(ReportExport export, String scope) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", export.getType().name());
        details.put("format", export.getFormat().name());
        details.put("scope", scope);
        details.put("start", export.getWindowStart().toString());
        details.put("end", export.getWindowEnd().toString());
        return details;
    }

    private static String describe(RuntimeException e) {
        return e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage()
                : e.getClass().getSimpleName();
    }

    // --- Requests -------------------------------------------------------------------------------------

    /** Inclusive days in the analytics timezone. */
    record Days(LocalDate start, LocalDate end) {

        long count() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }
    }

    /** The window the analytics page would use (the last 30 days by default), within the report limit. */
    Days window(@Nullable LocalDate start, @Nullable LocalDate end) {
        LocalDate last = end != null ? end : LocalDate.now(clock.withZone(zone));
        LocalDate first = start != null ? start : last.minusDays(DEFAULT_DAYS - 1);
        if (last.isBefore(first)) {
            throw ValidationException.onField("end", "must be on or after start");
        }
        Days days = new Days(first, last);
        if (days.count() > properties.maxWindowDays()) {
            throw ValidationException.onField("start", "a report covers at most " + properties.maxWindowDays()
                    + " days");
        }
        return days;
    }

    private @Nullable Team team(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.strip();
        Team team = teams.findByName(trimmed)
                .orElseThrow(() -> ValidationException.onField("team", "Unknown team '" + trimmed + "'"));
        access.requireTeam(team.getId());
        return team;
    }

    /** fairness-analytics-sre-2026-09-14-to-2026-10-13.pdf */
    static String fileName(String type, String subject, Days days, ReportExport.Format format) {
        String slug = subject.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "fairness-" + type + "-" + (slug.isEmpty() ? "report" : slug) + "-" + days.start() + "-to-"
                + days.end() + "." + format.extension();
    }
}
