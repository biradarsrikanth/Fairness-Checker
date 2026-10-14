package com.example.fairnesstracker.report;

import com.example.fairnesstracker.alert.dto.AlertResponse;
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
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import com.example.fairnesstracker.report.ReportData.Header;
import com.example.fairnesstracker.report.ReportData.Rows;
import com.example.fairnesstracker.scoring.dto.Scores;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Report data as the services would gather it, for rendering tests. */
final class ReportFixtures {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final LocalDate START = LocalDate.of(2026, 9, 14);
    static final LocalDate END = LocalDate.of(2026, 10, 13);
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private ReportFixtures() {
    }

    static Header header(String title, String scope) {
        return new Header(title, scope, START, END, Instant.parse("2026-10-13T18:30:00Z"), "manager@example.com", ZONE);
    }

    static Scores.BurnoutScore burnout(long id, String name, double score, String risk) {
        return new Scores.BurnoutScore(id, name, "SRE", 12, 1, score, risk, 40, 20, 25, 1.5, 4.0, 120.0,
                new Scores.BurnoutComponents(0.7, 0.6, 0.5));
    }

    static ReportData.Analytics analytics(boolean scoresAvailable) {
        Window window = new Window(START, END, ZONE.getId());
        Window previous = new Window(START.minusDays(30), START.minusDays(1), ZONE.getId());
        Summary summary = new Summary(window, previous, "SRE", new Kpi(20.0, 16.0, 25.0), new Kpi(5.0, 5.0, 0.0),
                new Kpi(42.0, 60.0, -30.0), new Kpi(35.0, 30.0, 16.7), new Kpi(5.0, 10.0, -50.0),
                scoresAvailable ? new Kpi(0.312, 0.25, 24.8) : null, scoresAvailable ? "MODERATE" : null,
                scoresAvailable ? new Kpi(1.0, 0.0, null) : null,
                scoresAvailable ? ScoresStatus.AVAILABLE : new ScoresStatus(false, "SCORER_UNAVAILABLE"));
        List<SeriesPoint> points = new ArrayList<>();
        for (int week = 0; week < 5; week++) {
            points.add(new SeriesPoint(START.plusWeeks(week), 4 + week, new SeverityCounts(1, 0, 2 + week, 1, 0, 0)));
        }
        List<HeatmapCell> cells = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                cells.add(new HeatmapCell(day, hour, (day * hour) % 5));
            }
        }
        Scores.Window scoreWindow = new Scores.Window(START.atStartOfDay().atOffset(IST),
                END.plusDays(1).atStartOfDay().atOffset(IST), ZONE.getId());
        List<Scores.TrendPeriod> periods = new ArrayList<>();
        for (int week = 0; week < 5; week++) {
            OffsetDateTime from = START.plusWeeks(week).atStartOfDay().atOffset(IST);
            periods.add(new Scores.TrendPeriod(from, from.plusWeeks(1), 4 + week, 0.2 + week * 0.05, "W" + week,
                    week == 4 ? "LOW" : "NORMAL", 30.0, week % 2));
        }
        List<Scores.EngineerLoad> loads = List.of(
                new Scores.EngineerLoad(3, "Asha Rao", "SRE", 12, 1, 30, 60, 120.0, 0.25),
                new Scores.EngineerLoad(4, "Ravi Kumar", "SRE", 8, 0, 20, 40, 120.0, 0.17));
        Snapshot snapshot = new Snapshot(summary, new TimeSeries(window, "SRE", "week", points),
                new Heatmap(window, "SRE", 4, cells),
                new TopServices(window, "SRE", List.of(new ServiceStats(1L, "payments-api", 9, 30.0, 44.4))),
                new Trend(window, "SRE", "week", scoresAvailable ? periods : List.of(),
                        scoresAvailable ? ScoresStatus.AVAILABLE : summary.scores()),
                scoresAvailable ? loads : List.of(),
                scoresAvailable ? List.of(burnout(3, "Asha Rao", 64, "HIGH"), burnout(4, "Ravi Kumar", 20, "LOW"))
                        : List.of(),
                scoresAvailable ? List.of(new Scores.TeamFairness("SRE", 0.31, "MODERATE", 0.0, 20, 2)) : List.of());
        Scores.TimeOfDayReport timeOfDay = scoresAvailable ? new Scores.TimeOfDayReport(scoreWindow, "SRE", List.of(
                new Scores.EngineerTimeOfDay(3, "Asha Rao", "SRE", 4, 2, 3, 3))) : null;
        return new ReportData.Analytics(header("Fairness report", "SRE"), snapshot, timeOfDay,
                new Rows<>(alerts(30), 31), new Rows<>(shifts(), 2));
    }

    /** The analytics report over two teams: each team's own fairness is listed. */
    static ReportData.Analytics analyticsOfTwoTeams() {
        ReportData.Analytics one = analytics(true);
        Snapshot s = one.snapshot();
        Snapshot both = new Snapshot(s.summary(), s.alertsOverTime(), s.heatmap(), s.topServices(), s.trend(),
                s.loadShare(), s.burnout(), List.of(new Scores.TeamFairness("SRE", 0.31, "MODERATE", 0.0, 20, 2),
                new Scores.TeamFairness("Platform", 0.62, "CRITICAL", null, 5, 3)));
        return new ReportData.Analytics(one.header(), both, one.timeOfDay(), one.alerts(), one.shifts());
    }

    static ReportData.Engineer engineer() {
        return engineer(true);
    }

    static ReportData.Engineer engineer(boolean scored) {
        Scores.Window scoreWindow = new Scores.Window(START.atStartOfDay().atOffset(IST),
                END.plusDays(1).atStartOfDay().atOffset(IST), ZONE.getId());
        Scores.EngineerReport report = new Scores.EngineerReport(scoreWindow,
                new Scores.EngineerRef(3, "Asha Rao", "SRE"), true, burnout(3, "Asha Rao", 64, "HIGH"), 60,
                new Scores.TimeOfDayCounts(4, 2, 3, 3));
        return new ReportData.Engineer(header("Engineer report", "Asha Rao · SRE"), report.engineer(), true,
                scored ? report : null, new Rows<>(alerts(3), 3), new Rows<>(shifts(), 2));
    }

    static List<AlertResponse> alerts(int count) {
        List<AlertResponse> alerts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Instant at = Instant.parse("2026-10-10T18:00:00Z").minusSeconds(3600L * i);
            alerts.add(new AlertResponse((long) i + 1, 3L, "Asha Rao", "SRE", i % 3 == 0 ? "P2" : null, "resolved",
                    "high", at, at.plusSeconds(240), at.plusSeconds(5400), "Q" + i, 800 + i,
                    "payments-api: error rate high <" + i + "> & more", "payments-api", "Asha Rao", "API_SYNC"));
        }
        return alerts;
    }

    static List<OncallShiftResponse> shifts() {
        return List.of(
                new OncallShiftResponse(1L, 3L, "Asha Rao", "SRE", 1, Instant.parse("2026-10-05T18:30:00Z"),
                        Instant.parse("2026-10-12T18:30:00Z")),
                new OncallShiftResponse(2L, 4L, "Ravi Kumar", "SRE", 2, Instant.parse("2026-10-05T18:30:00Z"),
                        Instant.parse("2026-10-12T18:30:00Z")));
    }
}
