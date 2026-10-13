package com.example.fairnesstracker.analytics.dto;

import com.example.fairnesstracker.scoring.dto.Scores;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/** The analytics page's data: what the gateway counts itself, plus the scorer's scores. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /**
     * Bound from query parameters. Dates are inclusive days in the analytics timezone (the scorer's
     * TEAM_TIMEZONE); without them, the last 30 days. Without {@code team}: every team you can see.
     */
    public record AnalyticsFilter(
            @Size(max = 255) String team,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @Pattern(regexp = "day|week|month", message = "must be one of day, week, month") String granularity
    ) {
    }

    @Schema(description = "Inclusive dates in the given timezone")
    public record Window(LocalDate start, LocalDate end, String timezone) {
    }

    @Schema(description = "A figure for the window and the same-length period before it. changePct is null when "
            + "there's nothing to compare with")
    public record Kpi(@Nullable Double value, @Nullable Double previous, @Nullable Double changePct) {
    }

    @Schema(description = "gini, fairnessLabel and highRiskEngineers come from the scorer and are null when "
            + "scores aren't available (see scores); highRiskEngineers is also null for engineers")
    public record Summary(Window window, Window previousWindow, @Nullable String team, Kpi alerts, Kpi mttaMinutes,
                          Kpi mttrMinutes, Kpi afterHoursPct, Kpi escalationRatePct, @Nullable Kpi gini, @Nullable String fairnessLabel,
                          @Nullable Kpi highRiskEngineers, ScoresStatus scores) {
    }

    @Schema(description = "available: whether the scorer's figures are included. reason when not: "
            + "SCORER_UNAVAILABLE, or SEVERAL_TEAMS (pick one team to get scores)")
    public record ScoresStatus(boolean available, @Nullable String reason) {

        public static final ScoresStatus AVAILABLE = new ScoresStatus(true, null);
    }

    public record SeverityCounts(int p1, int p2, int p3, int p4, int p5, int other) {
    }

    @Schema(description = "One day, ISO week (from Monday) or month, by its first day")
    public record SeriesPoint(LocalDate start, int total, SeverityCounts bySeverity) {
    }

    public record TimeSeries(Window window, @Nullable String team, String granularity, List<SeriesPoint> points) {
    }

    @Schema(description = "dayOfWeek: 1 = Monday ... 7 = Sunday; hour 0-23. Both in each team's own timezone")
    public record HeatmapCell(int dayOfWeek, int hour, int count) {
    }

    @Schema(description = "All 168 weekday-hour cells, zeros included")
    public record Heatmap(Window window, @Nullable String team, int max, List<HeatmapCell> cells) {
    }

    public record ServiceStats(@Nullable Long serviceId, String name, int alertCount, @Nullable Double mttrMinutes, double afterHoursPct) {
    }

    public record TopServices(Window window, @Nullable String team, List<ServiceStats> services) {
    }

    public record Trend(Window window, @Nullable String team, String granularity, List<Scores.TrendPeriod> periods,
                        ScoresStatus scores) {
    }

    @Schema(description = "Everything the analytics page and the reports show, computed once. loadShare and "
            + "burnout are limited to your own row if you're an engineer")
    public record Snapshot(Summary summary, TimeSeries alertsOverTime, Heatmap heatmap, TopServices topServices,
                           Trend trend, List<Scores.EngineerLoad> loadShare, List<Scores.BurnoutScore> burnout) {
    }
}
