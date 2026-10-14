package com.example.fairnesstracker.report.render;

import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Kpi;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.SeriesPoint;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.ServiceStats;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Snapshot;
import com.example.fairnesstracker.analytics.dto.AnalyticsDtos.Summary;
import com.example.fairnesstracker.oncall.dto.OncallShiftResponse;
import com.example.fairnesstracker.report.ReportData;
import com.example.fairnesstracker.report.ReportData.Header;
import com.example.fairnesstracker.report.ReportData.Rows;
import com.example.fairnesstracker.report.ReportExport;
import com.example.fairnesstracker.report.ReportRenderer;
import com.example.fairnesstracker.report.render.ReportText.Kind;
import com.example.fairnesstracker.scoring.dto.Scores;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reports as Excel workbooks: one sheet per part, real numbers and dates (so they sort, sum and chart), in
 * the analytics timezone. Written with POI's streaming API, which keeps only a window of rows in memory.
 *
 * <p>Analytics: Summary, Fairness, Burnout, Time of day, Alerts, On-call. Engineer: Summary, Time of day,
 * Alerts, On-call.
 */
@Component
public class ExcelReportRenderer implements ReportRenderer {

    private static final int ROWS_IN_MEMORY = 200;

    @Override
    public ReportExport.Format format() {
        return ReportExport.Format.XLSX;
    }

    @Override
    public byte[] render(ReportData data) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROWS_IN_MEMORY)) {
            Styles styles = new Styles(workbook);
            switch (data) {
                case ReportData.Analytics analytics -> analytics(workbook, styles, analytics);
                case ReportData.Engineer engineer -> engineer(workbook, styles, engineer);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- Analytics ------------------------------------------------------------------------------------

    private void analytics(SXSSFWorkbook workbook, Styles styles, ReportData.Analytics data) {
        Header header = data.header();
        Snapshot snapshot = data.snapshot();
        Summary summary = snapshot.summary();
        ZoneId zone = header.zone();

        SheetWriter sheet = new SheetWriter(workbook.createSheet("Summary"), styles, 28, 16, 16, 16);
        headerRows(sheet, header);
        sheet.text("Compared with", ReportText.range(summary.previousWindow().start(), summary.previousWindow().end()));
        sheet.blank();
        sheet.headings("Figure", "This period", "Previous period", "Change");
        kpi(sheet, "Alerts", Kind.COUNT, summary.alerts(), styles.integer);
        kpi(sheet, "Time to acknowledge (minutes, average)", Kind.MINUTES, summary.mttaMinutes(), styles.decimal);
        kpi(sheet, "Time to resolve (minutes, average)", Kind.MINUTES, summary.mttrMinutes(), styles.decimal);
        kpi(sheet, "After hours (%)", Kind.PCT, summary.afterHoursPct(), styles.decimal);
        kpi(sheet, "Escalated (%)", Kind.PCT, summary.escalationRatePct(), styles.decimal);
        if (summary.gini() != null) {
            kpi(sheet, "Fairness (Gini, 0 = even)", Kind.GINI, summary.gini(), styles.gini);
            sheet.text("Fairness", ReportText.humanize(summary.fairnessLabel()));
        }
        if (summary.highRiskEngineers() != null) {
            kpi(sheet, "Engineers at high burnout risk", Kind.COUNT, summary.highRiskEngineers(), styles.integer);
        }
        if (!summary.scores().available()) {
            sheet.blank();
            sheet.note(ReportText.scoresMissing(summary.scores()));
        }

        sheet.blank();
        sheet.title("Alerts per " + snapshot.alertsOverTime().granularity());
        sheet.headings("Period starting", "P1", "P2", "P3", "P4", "P5", "No severity", "Total");
        for (SeriesPoint point : snapshot.alertsOverTime().points()) {
            var s = point.bySeverity();
            sheet.row().date(point.start()).num(s.p1()).num(s.p2()).num(s.p3()).num(s.p4()).num(s.p5()).num(s.other())
                    .num(point.total());
        }

        sheet.blank();
        sheet.title("Noisiest services");
        sheet.headings("Service", "Alerts", "Time to resolve (minutes)", "After hours (%)");
        for (ServiceStats service : snapshot.topServices().services()) {
            sheet.row().text(service.name()).num(service.alertCount()).dec(service.mttrMinutes())
                    .dec(service.afterHoursPct());
        }

        fairness(workbook, styles, snapshot, zone);
        burnout(workbook, styles, snapshot);
        timeOfDay(workbook, styles, data.timeOfDay(), summary);
        alerts(workbook, styles, data.alerts(), zone);
        shifts(workbook, styles, data.shifts(), zone);
    }

    private void kpi(SheetWriter sheet, String label, Kind kind, Kpi kpi, CellStyle style) {
        String change = ReportText.change(kind, kpi);
        sheet.row().text(label).num(kpi.value(), style).num(kpi.previous(), style).text(change == null ? "–" : change);
    }

    private void fairness(SXSSFWorkbook workbook, Styles styles, Snapshot snapshot, ZoneId zone) {
        SheetWriter sheet = new SheetWriter(workbook.createSheet("Fairness"), styles, 24, 14, 12, 14, 12, 12, 14, 16);
        sheet.title("Share of the on-call load");
        if (!snapshot.summary().scores().available()) {
            sheet.note(ReportText.scoresMissing(snapshot.summary().scores()));
            return;
        }
        sheet.headings("Engineer", "Team", "Alerts", "Escalated to them", "Load", "Share (%)", "On-call hours",
                "Load per on-call hour");
        for (Scores.EngineerLoad load : snapshot.loadShare()) {
            sheet.row().text(load.name()).text(load.team()).num(load.alertCount()).num(load.escalationsReceived())
                    .dec(load.load()).dec(load.sharePct()).dec(load.oncallHours()).dec(load.loadPerOncallHour());
        }
        if (snapshot.teamFairness().size() > 1) {
            sheet.blank();
            sheet.title("Fairness by team (the overall Gini is their average, weighted by alerts)");
            sheet.headings("Team", "Gini", "Label", "Alerts", "Engineers", "Rotation Gini");
            for (Scores.TeamFairness team : snapshot.teamFairness()) {
                sheet.row().text(team.team()).num(team.gini(), styles.gini).text(ReportText.humanize(team.label()))
                        .num(team.alertCount()).num(team.engineerCount()).num(team.rotationGini(), styles.gini);
            }
        }
        sheet.blank();
        sheet.title("Fairness per " + snapshot.trend().granularity());
        sheet.headings("Period starting", "Gini", "Alerts", "Average burnout", "High risk", "Confidence");
        for (Scores.TrendPeriod period : snapshot.trend().periods()) {
            sheet.row().date(period.start().atZoneSameInstant(zone).toLocalDate()).num(period.gini(), styles.gini)
                    .num(period.alertCount()).dec(period.averageBurnout()).num(period.highRiskCount())
                    .text(ReportText.humanize(period.confidence()));
        }
    }

    private void burnout(SXSSFWorkbook workbook, Styles styles, Snapshot snapshot) {
        SheetWriter sheet = new SheetWriter(workbook.createSheet("Burnout"), styles,
                24, 14, 10, 10, 10, 10, 10, 10, 10, 16, 14, 12, 14, 10);
        sheet.title("Burnout risk (score 0–100)");
        if (!snapshot.summary().scores().available()) {
            sheet.note(ReportText.scoresMissing(snapshot.summary().scores()));
            return;
        }
        sheet.headings("Engineer", "Team", "Score", "Risk", "Alerts", "Night (%)", "Evening (%)", "Weekend (%)",
                "Trend (×)", "Median ack (minutes)", "On-call hours", "Exposure", "Relative load", "Trend part");
        snapshot.burnout().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .forEach(b -> sheet.row().text(b.name()).text(b.team()).dec(b.score())
                        .text(ReportText.humanize(b.risk())).num(b.alertCount()).dec(b.nightPct())
                        .dec(b.eveningPct()).dec(b.weekendPct()).dec(b.trendRatio()).dec(b.medianAckMinutes())
                        .dec(b.oncallHours()).num(b.components().exposure(), styles.gini)
                        .num(b.components().relativeLoad(), styles.gini).num(b.components().trend(), styles.gini));
    }

    private void timeOfDay(SXSSFWorkbook workbook, Styles styles, Scores.@Nullable TimeOfDayReport report,
                           Summary summary) {
        SheetWriter sheet = new SheetWriter(workbook.createSheet("Time of day"), styles, 24, 14, 10, 10, 10, 10, 10);
        sheet.title("Alerts by part of the day (team's local time)");
        if (report == null) {
            sheet.note(summary.scores().available() ? "The scoring service didn't answer for time of day."
                    : ReportText.scoresMissing(summary.scores()));
            return;
        }
        sheet.headings("Engineer", "Team", "Morning 06–12", "Afternoon 12–18", "Evening 18–23", "Night 23–06",
                "Total");
        for (Scores.EngineerTimeOfDay row : ReportText.busiestFirst(report.engineers())) {
            sheet.row().text(row.name()).text(row.team()).num(row.morning()).num(row.afternoon()).num(row.evening())
                    .num(row.night()).num(ReportText.total(row));
        }
    }

    // --- Engineer -------------------------------------------------------------------------------------

    private void engineer(SXSSFWorkbook workbook, Styles styles, ReportData.Engineer data) {
        SheetWriter sheet = new SheetWriter(workbook.createSheet("Summary"), styles, 34, 18);
        headerRows(sheet, data.header());
        sheet.text("Status", data.active() ? "Active" : "Inactive");
        sheet.blank();
        if (data.report() != null) {
            scores(workbook, styles, sheet, data.report());
        } else {
            sheet.note("The scores are missing: the scoring service didn't answer when this report was made. "
                    + "The alerts and shifts are complete.");
        }
        alerts(workbook, styles, data.alerts(), data.header().zone());
        shifts(workbook, styles, data.shifts(), data.header().zone());
    }

    private void scores(SXSSFWorkbook workbook, Styles styles, SheetWriter sheet, Scores.EngineerReport report) {
        Scores.BurnoutScore burnout = report.burnout();
        sheet.headings("Figure", "Value");
        sheet.row().text("Burnout score (0–100)").dec(burnout.score());
        sheet.text("Risk", ReportText.humanize(burnout.risk()));
        sheet.row().text("Out-of-hours exposure (0–1, weight 50)").num(burnout.components().exposure(), styles.gini);
        sheet.row().text("Relative load (0–1, weight 30)").num(burnout.components().relativeLoad(), styles.gini);
        sheet.row().text("Trend (0–1, weight 20)").num(burnout.components().trend(), styles.gini);
        sheet.row().text("Alerts").num(burnout.alertCount());
        sheet.row().text("Escalated to them").num(burnout.escalationsReceived());
        sheet.row().text("Share of team load (%)").dec(report.teamSharePct());
        sheet.row().text("Night (%)").dec(burnout.nightPct());
        sheet.row().text("Evening (%)").dec(burnout.eveningPct());
        sheet.row().text("Weekend (%)").dec(burnout.weekendPct());
        sheet.row().text("Trend (last 30 days ÷ the 30 before)").dec(burnout.trendRatio());
        sheet.row().text("Median time to acknowledge (minutes)").dec(burnout.medianAckMinutes());
        sheet.row().text("On-call hours").dec(burnout.oncallHours());

        SheetWriter times = new SheetWriter(workbook.createSheet("Time of day"), styles, 20, 14, 10);
        times.title("Alerts by part of the day (team's local time)");
        times.headings("Part of the day", "Hours", "Alerts");
        Scores.TimeOfDayCounts counts = report.timeOfDay();
        times.row().text("Morning").text("06:00–12:00").num(counts.morning());
        times.row().text("Afternoon").text("12:00–18:00").num(counts.afternoon());
        times.row().text("Evening").text("18:00–23:00").num(counts.evening());
        times.row().text("Night").text("23:00–06:00").num(counts.night());
    }

    // --- Shared sheets --------------------------------------------------------------------------------

    private void headerRows(SheetWriter sheet, Header header) {
        sheet.title(header.title());
        sheet.text("Covers", header.scope());
        sheet.text("Dates", ReportText.range(header.start(), header.end()) + " (" + header.zone().getId() + ")");
        sheet.text("Generated", ReportText.time(header.generatedAt(), header.zone())
                + (header.generatedBy() != null ? " by " + header.generatedBy() : ""));
    }

    private void alerts(SXSSFWorkbook workbook, Styles styles, Rows<AlertResponse> alerts, ZoneId zone) {
        SheetWriter sheet = new SheetWriter(workbook.createSheet("Alerts"), styles,
                18, 18, 18, 40, 20, 20, 14, 10, 14, 10, 14, 14, 18);
        sheet.headings("Triggered", "Acknowledged", "Resolved", "Alert", "Service", "Paged", "Team", "Severity",
                "Status", "Urgency", "Minutes to acknowledge", "Minutes to resolve", "PagerDuty incident");
        sheet.freezeHeader();
        for (AlertResponse alert : alerts.items()) {
            sheet.row().time(alert.triggeredAt(), zone).time(alert.acknowledgedAt(), zone).time(alert.resolvedAt(), zone)
                    .text(alert.title()).text(alert.serviceName())
                    .text(alert.engineerName() != null ? alert.engineerName() : alert.assignedEngineerName())
                    .text(alert.team()).text(alert.severity()).text(ReportText.humanize(alert.status()))
                    .text(alert.urgency()).dec(minutesBetween(alert.triggeredAt(), alert.acknowledgedAt()))
                    .dec(minutesBetween(alert.triggeredAt(), alert.resolvedAt())).text(alert.pagerDutyIncidentId());
        }
        if (alerts.truncated()) {
            sheet.blank();
            sheet.note("The latest " + alerts.items().size() + " of " + alerts.total() + " alerts.");
        }
    }

    private void shifts(SXSSFWorkbook workbook, Styles styles, Rows<OncallShiftResponse> shifts, ZoneId zone) {
        SheetWriter sheet = new SheetWriter(workbook.createSheet("On-call"), styles, 24, 18, 12, 18, 18, 10);
        sheet.headings("Engineer", "Team", "Level", "Starts", "Ends", "Hours");
        sheet.freezeHeader();
        for (OncallShiftResponse shift : shifts.items()) {
            sheet.row().text(shift.engineerName()).text(shift.team()).text(shift.escalationLevel() == 1 ? "Primary"
                            : "Level " + shift.escalationLevel()).time(shift.startsAt(), zone).time(shift.endsAt(), zone)
                    .dec(Duration.between(shift.startsAt(), shift.endsAt()).toMinutes() / 60.0);
        }
        if (shifts.items().isEmpty()) {
            sheet.note("No on-call shifts in these dates; they come from the PagerDuty sync.");
        }
        if (shifts.truncated()) {
            sheet.blank();
            sheet.note("The first " + shifts.items().size() + " of " + shifts.total() + " shifts.");
        }
    }

    private static @Nullable Double minutesBetween(Instant from, @Nullable Instant to) {
        return to == null ? null : Duration.between(from, to).toSeconds() / 60.0;
    }

    // --- Writing --------------------------------------------------------------------------------------

    /** The workbook's cell styles, made once (a workbook holds at most 64,000). */
    private static final class Styles {

        final CellStyle title;
        final CellStyle heading;
        final CellStyle label;
        final CellStyle note;
        final CellStyle integer;
        final CellStyle decimal;
        final CellStyle gini;
        final CellStyle date;
        final CellStyle dateTime;

        Styles(SXSSFWorkbook workbook) {
            var format = workbook.createDataFormat();
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font big = workbook.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            Font italic = workbook.createFont();
            italic.setItalic(true);

            title = workbook.createCellStyle();
            title.setFont(big);
            heading = workbook.createCellStyle();
            heading.setFont(bold);
            heading.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            heading.setBorderBottom(BorderStyle.THIN);
            heading.setWrapText(true);
            label = workbook.createCellStyle();
            label.setFont(bold);
            note = workbook.createCellStyle();
            note.setFont(italic);
            integer = workbook.createCellStyle();
            integer.setDataFormat(format.getFormat("#,##0"));
            decimal = workbook.createCellStyle();
            decimal.setDataFormat(format.getFormat("#,##0.0"));
            gini = workbook.createCellStyle();
            gini.setDataFormat(format.getFormat("0.000"));
            date = workbook.createCellStyle();
            date.setDataFormat(format.getFormat("yyyy-mm-dd"));
            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(format.getFormat("yyyy-mm-dd hh:mm"));
        }
    }

    /** Appends rows to a sheet, a cell at a time. */
    private static final class SheetWriter {

        private final SXSSFSheet sheet;
        private final Styles styles;
        private int next;

        SheetWriter(SXSSFSheet sheet, Styles styles, int... widths) {
            this.sheet = sheet;
            this.styles = styles;
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
        }

        RowWriter row() {
            return new RowWriter(sheet.createRow(next++), styles);
        }

        void blank() {
            next++;
        }

        void title(String text) {
            Cell cell = sheet.createRow(next++).createCell(0);
            cell.setCellValue(text);
            cell.setCellStyle(styles.title);
        }

        void note(String text) {
            Cell cell = sheet.createRow(next++).createCell(0);
            cell.setCellValue(text);
            cell.setCellStyle(styles.note);
        }

        void text(String label, String value) {
            Row row = sheet.createRow(next++);
            Cell first = row.createCell(0);
            first.setCellValue(label);
            first.setCellStyle(styles.label);
            row.createCell(1).setCellValue(value);
        }

        void headings(String... names) {
            Row row = sheet.createRow(next++);
            for (int i = 0; i < names.length; i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(names[i]);
                cell.setCellStyle(styles.heading);
            }
        }

        /** Keeps the heading row in view (call right after a sheet's first row). */
        void freezeHeader() {
            sheet.createFreezePane(0, next);
        }
    }

    private static final class RowWriter {

        private final Row row;
        private final Styles styles;
        private int column;

        RowWriter(Row row, Styles styles) {
            this.row = row;
            this.styles = styles;
        }

        RowWriter text(@Nullable String value) {
            Cell cell = row.createCell(column++);
            if (value != null) {
                cell.setCellValue(value);
            }
            return this;
        }

        RowWriter num(long value) {
            return num((double) value, styles.integer);
        }

        RowWriter dec(@Nullable Double value) {
            return num(value, styles.decimal);
        }

        RowWriter num(@Nullable Double value, CellStyle style) {
            Cell cell = row.createCell(column++);
            if (value != null) {
                cell.setCellValue(value);
                cell.setCellStyle(style);
            }
            return this;
        }

        RowWriter date(LocalDate value) {
            Cell cell = row.createCell(column++);
            cell.setCellValue(value);
            cell.setCellStyle(styles.date);
            return this;
        }

        /** An instant as the local time in {@code zone}: Excel has no timezones. */
        RowWriter time(@Nullable Instant value, ZoneId zone) {
            Cell cell = row.createCell(column++);
            if (value != null) {
                cell.setCellValue(value.atZone(zone).toLocalDateTime());
                cell.setCellStyle(styles.dateTime);
            }
            return this;
        }
    }
}
