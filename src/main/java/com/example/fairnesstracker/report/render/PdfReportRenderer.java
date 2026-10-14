package com.example.fairnesstracker.report.render;

import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.HeatmapCell;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Kpi;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Snapshot;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Summary;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import com.example.fairnesstracker.report.ReportData;
import com.example.fairnesstracker.report.ReportData.Header;
import com.example.fairnesstracker.report.ReportExport;
import com.example.fairnesstracker.report.ReportRenderer;
import com.example.fairnesstracker.report.render.ReportText.Kind;
import com.example.fairnesstracker.scoring.dto.Scores;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reports as PDF: a Thymeleaf template (resources/reports) filled with figures worded here, charts from
 * {@link ChartRenderer}, laid out by openhtmltopdf in the bundled DejaVu Sans. The workbook has every row; the
 * PDF is the summary to read and pass on.
 */
@Component
public class PdfReportRenderer implements ReportRenderer {

    /** Alerts the PDF lists; the workbook has them all. */
    static final int RECENT_ALERTS = 25;

    private static final String[] WEEKDAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    // The web app's sequential blue (charts/palette.ts); empty cells neutral
    private static final String[] SEQUENTIAL = {"#cde2fb", "#9ec5f4", "#6da7ec", "#3987e5", "#256abf", "#184f95",
            "#0d366b"};
    private static final String EMPTY_CELL = "#f0efec";

    private final TemplateEngine templates;
    private final ChartRenderer charts;

    public PdfReportRenderer(ChartRenderer charts) {
        this.charts = charts;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("reports/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        this.templates = new TemplateEngine();
        this.templates.setTemplateResolver(resolver);
    }

    @Override
    public ReportExport.Format format() {
        return ReportExport.Format.PDF;
    }

    @Override
    public byte[] render(ReportData data) {
        Context context = new Context();
        context.setVariable("header", header(data.header()));
        String template = switch (data) {
            case ReportData.Analytics analytics -> {
                analytics(context, analytics);
                yield "analytics";
            }
            case ReportData.Engineer engineer -> {
                engineer(context, engineer);
                yield "engineer";
            }
        };
        return pdf(templates.process(template, context));
    }

    // --- View models ----------------------------------------------------------------------------------

    public record HeaderView(String title, String scope, String dates, String generated, String timezone) {
    }

    public record KpiView(String label, String value, @Nullable String previous, @Nullable String change,
                          boolean worse, @Nullable String note) {
    }

    public record CellView(int count, String background, boolean dark) {
    }

    public record HeatmapRow(String day, List<CellView> cells) {
    }

    public record AlertRow(String triggered, String title, String service, String paged, String severity,
                           String status) {
    }

    public record PartView(String label, String description, String points, int weight, double percent) {
    }

    private HeaderView header(Header header) {
        return new HeaderView(header.title(), header.scope(), ReportText.range(header.start(), header.end()),
                ReportText.time(header.generatedAt(), header.zone())
                        + (header.generatedBy() != null ? " by " + header.generatedBy() : ""),
                header.zone().getId());
    }

    private void analytics(Context context, ReportData.Analytics data) {
        Snapshot snapshot = data.snapshot();
        Summary summary = snapshot.summary();
        ZoneId zone = data.header().zone();
        context.setVariable("comparedWith",
                ReportText.range(summary.previousWindow().start(), summary.previousWindow().end()));
        context.setVariable("kpis", kpis(summary));
        context.setVariable("scoresNote", summary.scores().available() ? null : ReportText.scoresMissing(summary.scores()));
        boolean anyAlerts = summary.alerts().value() != null && summary.alerts().value() > 0;
        context.setVariable("granularity", snapshot.alertsOverTime().granularity());
        context.setVariable("alertsChart", anyAlerts ? charts.alertsOverTime(snapshot.alertsOverTime(), 520, 220) : null);
        context.setVariable("giniChart", snapshot.trend().periods().isEmpty() ? null
                : charts.giniTrend(snapshot.trend().periods(), snapshot.trend().granularity(), zone, 520, 200));
        context.setVariable("trendGranularity", snapshot.trend().granularity());
        context.setVariable("loadChart", snapshot.loadShare().isEmpty() ? null : charts.loadShare(snapshot.loadShare(), 520));
        context.setVariable("heatmap", anyAlerts ? heatmap(snapshot.heatmap().cells(), snapshot.heatmap().max()) : null);
        context.setVariable("hours", hours());
        context.setVariable("burnout", snapshot.burnout().stream()
                .sorted(Comparator.comparingDouble(Scores.BurnoutScore::score).reversed())
                .map(b -> Map.of(
                        "name", b.name(), "team", b.team(), "score", String.valueOf(Math.round(b.score())),
                        "risk", ReportText.humanize(b.risk()), "riskClass", b.risk().toLowerCase(),
                        "alerts", String.valueOf(b.alertCount()), "night", ReportText.pct(b.nightPct()),
                        "weekend", ReportText.pct(b.weekendPct()),
                        "trend", String.format(java.util.Locale.UK, "×%.1f", b.trendRatio()),
                        "ack", ReportText.minutes(b.medianAckMinutes())))
                .toList());
        context.setVariable("severalTeams", snapshot.burnout().stream().map(Scores.BurnoutScore::team).distinct().count() > 1);
        context.setVariable("services", snapshot.topServices().services().stream()
                .map(s -> Map.of("name", s.name(), "alerts", String.valueOf(s.alertCount()),
                        "mttr", ReportText.minutes(s.mttrMinutes()), "afterHours", ReportText.pct(s.afterHoursPct())))
                .toList());
        context.setVariable("timeOfDay", data.timeOfDay() == null ? null
                : ReportText.busiestFirst(data.timeOfDay().engineers()));
        context.setVariable("alerts", recent(data.alerts().items(), zone));
        context.setVariable("alertTotal", data.alerts().total());
        context.setVariable("shiftCount", data.shifts().total());
        context.setVariable("shiftHours", hours(data.shifts().items()));
    }

    private void engineer(Context context, ReportData.Engineer data) {
        ZoneId zone = data.header().zone();
        context.setVariable("engineer", data.engineer());
        context.setVariable("active", data.active());
        context.setVariable("alerts", recent(data.alerts().items(), zone));
        context.setVariable("alertTotal", data.alerts().total());
        context.setVariable("shiftCount", data.shifts().total());
        context.setVariable("shiftHours", hours(data.shifts().items()));
        Scores.EngineerReport report = data.report();
        context.setVariable("scoresAvailable", report != null);
        if (report != null) {
            scores(context, report);
        }
    }

    private void scores(Context context, Scores.EngineerReport report) {
        Scores.BurnoutScore burnout = report.burnout();
        double confidence = Math.min(1, burnout.alertCount() / 5.0);
        context.setVariable("score", String.valueOf(Math.round(burnout.score())));
        context.setVariable("risk", ReportText.humanize(burnout.risk()));
        context.setVariable("riskClass", burnout.risk().toLowerCase());
        context.setVariable("stats", List.of(
                Map.of("label", "Alerts", "value", String.valueOf(burnout.alertCount())),
                Map.of("label", "Share of team load", "value", ReportText.pct(report.teamSharePct())),
                Map.of("label", "Median time to acknowledge", "value", ReportText.minutes(burnout.medianAckMinutes())),
                Map.of("label", "On-call hours", "value",
                        burnout.oncallHours() == null ? "–" : String.valueOf(Math.round(burnout.oncallHours()))),
                Map.of("label", "Escalated to them", "value", String.valueOf(burnout.escalationsReceived()))));
        context.setVariable("parts", List.of(
                part("Out-of-hours exposure", "Night, evening and weekend pages", burnout.components().exposure(), 50, confidence),
                part("Relative load", "Load compared with the rest of the team", burnout.components().relativeLoad(), 30, confidence),
                part("Trend", "Pages in the last 30 days against the 30 before", burnout.components().trend(), 20, confidence)));
        context.setVariable("scaled", confidence < 1 ? burnout.alertCount() + "/5" : null);
        context.setVariable("exposure", List.of(
                Map.of("label", "Night", "value", ReportText.pct(burnout.nightPct())),
                Map.of("label", "Evening", "value", ReportText.pct(burnout.eveningPct())),
                Map.of("label", "Weekend", "value", ReportText.pct(burnout.weekendPct())),
                Map.of("label", "Trend", "value", String.format(java.util.Locale.UK, "×%.1f", burnout.trendRatio()))));
        Scores.TimeOfDayCounts counts = report.timeOfDay();
        int total = counts.morning() + counts.afternoon() + counts.evening() + counts.night();
        context.setVariable("timeOfDayChart", total > 0 ? charts.timeOfDay(counts, 520, 180) : null);
    }

    private static PartView part(String label, String description, double component, int weight, double confidence) {
        double points = Math.round(weight * component * confidence * 10) / 10.0;
        return new PartView(label, description, points == Math.rint(points) ? String.valueOf((long) points)
                : String.valueOf(points), weight, Math.min(100, points / weight * 100));
    }

    private static List<KpiView> kpis(Summary summary) {
        List<KpiView> kpis = new ArrayList<>();
        kpis.add(kpi("Alerts", Kind.COUNT, summary.alerts(), null));
        kpis.add(kpi("Time to acknowledge", Kind.MINUTES, summary.mttaMinutes(), "Average"));
        kpis.add(kpi("Time to resolve", Kind.MINUTES, summary.mttrMinutes(), "Average"));
        kpis.add(kpi("After hours", Kind.PCT, summary.afterHoursPct(), "18:00–06:00 and weekends"));
        kpis.add(kpi("Escalated", Kind.PCT, summary.escalationRatePct(), "Of alerts"));
        if (summary.gini() != null) {
            kpis.add(kpi("Fairness (Gini)", Kind.GINI, summary.gini(), ReportText.humanize(summary.fairnessLabel())));
        }
        if (summary.highRiskEngineers() != null) {
            kpis.add(kpi("High burnout risk", Kind.COUNT, summary.highRiskEngineers(), "Engineers"));
        }
        return kpis;
    }

    private static KpiView kpi(String label, Kind kind, Kpi kpi, @Nullable String note) {
        String change = ReportText.change(kind, kpi);
        return new KpiView(label, ReportText.value(kind, kpi.value()),
                kpi.previous() == null ? null : ReportText.value(kind, kpi.previous()), change,
                ReportText.worse(change), note);
    }

    private static List<HeatmapRow> heatmap(List<HeatmapCell> cells, int max) {
        int[][] counts = new int[7][24];
        for (HeatmapCell cell : cells) {
            counts[cell.dayOfWeek() - 1][cell.hour()] = cell.count();
        }
        List<HeatmapRow> rows = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            List<CellView> row = new ArrayList<>();
            for (int hour = 0; hour < 24; hour++) {
                int count = counts[day][hour];
                if (count == 0) {
                    row.add(new CellView(0, EMPTY_CELL, false));
                } else {
                    int step = max <= 1 ? SEQUENTIAL.length - 1
                            : (int) Math.round((count - 1) / (double) (max - 1) * (SEQUENTIAL.length - 1));
                    row.add(new CellView(count, SEQUENTIAL[step], step >= 3));
                }
            }
            rows.add(new HeatmapRow(WEEKDAYS[day], row));
        }
        return rows;
    }

    private static List<String> hours() {
        List<String> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hours.add(String.format("%02d", hour));
        }
        return hours;
    }

    private static List<AlertRow> recent(List<AlertResponse> alerts, ZoneId zone) {
        return alerts.stream().limit(RECENT_ALERTS).map(a -> new AlertRow(
                ReportText.time(a.triggeredAt(), zone),
                a.title() != null ? a.title() : "Untitled alert",
                a.serviceName() != null ? a.serviceName() : "–",
                a.engineerName() != null ? a.engineerName()
                        : a.assignedEngineerName() != null ? a.assignedEngineerName() : "Unattributed",
                a.severity() != null ? a.severity() : "–",
                ReportText.humanize(a.status()))).toList();
    }

    private static long hours(List<OncallShiftResponse> shifts) {
        return Math.round(shifts.stream()
                .mapToDouble(s -> Duration.between(s.startsAt(), s.endsAt()).toMinutes() / 60.0).sum());
    }

    // --- PDF ------------------------------------------------------------------------------------------

    private static byte[] pdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> PdfReportRenderer.class.getResourceAsStream("/reports/fonts/DejaVuSans.ttf"),
                    "DejaVu Sans", 400, FontStyle.NORMAL, true);
            builder.useFont(() -> PdfReportRenderer.class.getResourceAsStream("/reports/fonts/DejaVuSans-Bold.ttf"),
                    "DejaVu Sans", 700, FontStyle.NORMAL, true);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
