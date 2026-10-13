package com.example.fairnesstracker.analytics;

import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.AnalyticsFilter;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Heatmap;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Snapshot;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Summary;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.TimeSeries;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.TopServices;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Trend;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The analytics page. Every endpoint takes {@code team}, {@code start}, {@code end} (inclusive days in the
 * analytics timezone; default the last 30) and {@code granularity} (day, week or month; chosen from the
 * window's length by default), and covers only what the caller may see.
 */
@Tag(name = "Analytics", description = "KPIs, alerts over time, weekday-hour heatmap, noisy services and trends")
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Operation(operationId = "getAnalyticsSummary", summary = "KPIs with the change from the previous period",
            description = "Alerts, MTTA, MTTR, after-hours %, escalation rate; Gini and high-risk engineers from "
                    + "the scorer when available.")
    @GetMapping("/summary")
    public Summary summary(@Valid @ParameterObject AnalyticsFilter filter) {
        return analytics.summary(filter);
    }

    @Operation(operationId = "getAlertsOverTime", summary = "Alert counts per period, by severity", description = "Every period, quiet ones as zeros.")
    @GetMapping("/alerts/timeseries")
    public TimeSeries alertsOverTime(@Valid @ParameterObject AnalyticsFilter filter) {
        return analytics.alertsOverTime(filter);
    }

    @Operation(operationId = "getAlertHeatmap", summary = "Alerts by weekday and hour", description = "In each team's own timezone; all 168 cells.")
    @GetMapping("/heatmap")
    public Heatmap heatmap(@Valid @ParameterObject AnalyticsFilter filter) {
        return analytics.heatmap(filter);
    }

    @Operation(operationId = "getTopServices", summary = "The ten services with the most alerts")
    @GetMapping("/services/top")
    public TopServices topServices(@Valid @ParameterObject AnalyticsFilter filter) {
        return analytics.topServices(filter);
    }

    @Operation(operationId = "getScoreTrend", summary = "Gini and burnout per week or month", description = "From the scorer; weekly unless "
            + "granularity=month or the window is longer than 110 weeks.")
    @GetMapping("/trend")
    public Trend trend(@Valid @ParameterObject AnalyticsFilter filter) {
        return analytics.trend(filter);
    }

    @Operation(operationId = "getAnalyticsSnapshot", summary = "Everything above in one response", description = "What the page loads at once and the "
            + "PDF and Excel reports are built from.")
    @GetMapping("/snapshot")
    public Snapshot snapshot(@Valid @ParameterObject AnalyticsFilter filter) {
        return analytics.snapshot(filter);
    }
}
