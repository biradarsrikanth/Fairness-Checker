package com.example.fairnesstracker.report;

import com.example.fairnesstracker.report.render.ChartRenderer;
import com.example.fairnesstracker.report.render.ExcelReportRenderer;
import com.example.fairnesstracker.report.render.PdfReportRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Renders reports from fixed data and reads them back: POI for the workbook, PDFBox for the PDF's text.
// The files land in target/report-samples for a look.
class ReportRenderersTest {

    private static final Path SAMPLES = Path.of("target", "report-samples");

    private static PdfReportRenderer pdf;
    private final ExcelReportRenderer excel = new ExcelReportRenderer();

    @BeforeAll
    static void setUp() throws IOException {
        System.setProperty("java.awt.headless", "true");
        pdf = new PdfReportRenderer(new ChartRenderer());
        Files.createDirectories(SAMPLES);
    }

    @Test
    void analyticsWorkbookHasEverySheetWithRealNumbers() throws IOException {
        byte[] file = excel.render(ReportFixtures.analytics(true));
        Files.write(SAMPLES.resolve("analytics.xlsx"), file);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(sheetNames(workbook))
                    .containsExactly("Summary", "Fairness", "Burnout", "Time of day", "Alerts", "On-call");

            Sheet summary = workbook.getSheet("Summary");
            Row alerts = find(summary, "Alerts");
            assertThat(alerts.getCell(1).getNumericCellValue()).isEqualTo(20);
            assertThat(alerts.getCell(2).getNumericCellValue()).isEqualTo(16);
            assertThat(alerts.getCell(3).getStringCellValue()).isEqualTo("+25%");
            assertThat(find(summary, "After hours (%)").getCell(3).getStringCellValue()).isEqualTo("+5 pts");
            assertThat(find(summary, "Fairness (Gini, 0 = even)").getCell(1).getNumericCellValue()).isEqualTo(0.312);

            Sheet burnout = workbook.getSheet("Burnout");
            // Highest score first
            assertThat(burnout.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Asha Rao");
            assertThat(burnout.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(64);

            Sheet alertSheet = workbook.getSheet("Alerts");
            // Heading + 30 alerts + a gap + the note that 31 exist
            assertThat(alertSheet.getRow(1).getCell(0).getLocalDateTimeCellValue())
                    .isEqualTo("2026-10-10T23:30");
            assertThat(new DataFormatter().formatCellValue(alertSheet.getRow(1).getCell(10))).isEqualTo("4.0");
            assertThat(alertSheet.getRow(32).getCell(0).getStringCellValue()).isEqualTo("The latest 30 of 31 alerts.");

            Sheet shifts = workbook.getSheet("On-call");
            assertThat(shifts.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(168);
            assertThat(shifts.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Level 2");
        }
    }

    @Test
    void workbookExplainsMissingScores() throws IOException {
        byte[] file = excel.render(ReportFixtures.analytics(false));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(text(workbook.getSheet("Summary"))).contains("The scoring service didn't answer");
            assertThat(text(workbook.getSheet("Burnout"))).contains("The scoring service didn't answer");
            assertThat(find(workbook.getSheet("Summary"), "Fairness (Gini, 0 = even)")).isNull();
        }
    }

    @Test
    void engineerWorkbook() throws IOException {
        byte[] file = excel.render(ReportFixtures.engineer());
        Files.write(SAMPLES.resolve("engineer.xlsx"), file);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(sheetNames(workbook)).containsExactly("Summary", "Time of day", "Alerts", "On-call");
            assertThat(find(workbook.getSheet("Summary"), "Burnout score (0–100)").getCell(1).getNumericCellValue())
                    .isEqualTo(64);
            assertThat(find(workbook.getSheet("Time of day"), "Night").getCell(2).getNumericCellValue()).isEqualTo(4);
        }
    }

    @Test
    void engineerWorkbookWithoutScoresStillListsAlertsAndShifts() throws IOException {
        byte[] file = excel.render(ReportFixtures.engineer(false));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(sheetNames(workbook)).containsExactly("Summary", "Alerts", "On-call");
            assertThat(text(workbook.getSheet("Summary"))).contains("the scoring service didn't answer");
            assertThat(find(workbook.getSheet("Summary"), "Burnout score (0–100)")).isNull();
        }
    }

    @Test
    void analyticsPdfReadsLikeThePage() throws IOException {
        byte[] file = pdf.render(ReportFixtures.analytics(true));
        Files.write(SAMPLES.resolve("analytics.pdf"), file);
        try (PDDocument document = Loader.loadPDF(file)) {
            savePages(document, "analytics");
            // Table cells wrap: compare with the line breaks collapsed
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Fairness report", "SRE", "14 Sept 2026 – 13 Oct 2026".replace("Sept", sep()),
                    "Key figures", "+25%", "(was 16)", "−30%", "+5 pts", "0.31", "Moderate", "Burnout", "Asha Rao",
                    "High", "Noisiest services", "payments-api", "Latest alerts", "The latest 25 of 31",
                    // Escaped as text, not markup
                    "<0> & more", "Page 1 of");
            // The fonts are embedded, so the minus sign and arrows print
            assertThat(document.getPage(0).getResources().getFontNames()).isNotEmpty();
        }
    }

    @Test
    void pdfWithoutScoresSaysWhy() throws IOException {
        byte[] file = pdf.render(ReportFixtures.analytics(false));
        try (PDDocument document = Loader.loadPDF(file)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("The scoring service didn't answer").doesNotContain("Fairness over time");
        }
    }

    @Test
    void engineerPdf() throws IOException {
        byte[] file = pdf.render(ReportFixtures.engineer());
        Files.write(SAMPLES.resolve("engineer.pdf"), file);
        try (PDDocument document = Loader.loadPDF(file)) {
            savePages(document, "engineer");
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Engineer report", "Asha Rao · SRE", "64", "High risk",
                    "What makes up the score", "35 of 50 points", "Time of day", "Latest alerts");
        }
    }

    @Test
    void engineerPdfWithoutScoresSaysWhy() throws IOException {
        byte[] file = pdf.render(ReportFixtures.engineer(false));
        try (PDDocument document = Loader.loadPDF(file)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Asha Rao · SRE", "the scoring service didn't answer", "Latest alerts")
                    .doesNotContain("What makes up the score");
        }
    }

    // --- Helpers --------------------------------------------------------------------------------------

    private static String sep() {
        return java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.UK)
                .format(java.time.LocalDate.of(2026, 9, 1));
    }

    private static void savePages(PDDocument document, String name) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            ImageIO.write(renderer.renderImageWithDPI(page, 80), "png",
                    SAMPLES.resolve(name + "-" + (page + 1) + ".png").toFile());
        }
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        List<String> names = new ArrayList<>();
        workbook.forEach(sheet -> names.add(sheet.getSheetName()));
        return names;
    }

    private static Row find(Sheet sheet, String label) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && label.equals(new DataFormatter().formatCellValue(row.getCell(0)))) {
                return row;
            }
        }
        return null;
    }

    private static String text(Sheet sheet) {
        StringBuilder text = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        sheet.forEach(row -> row.forEach(cell -> text.append(formatter.formatCellValue(cell)).append('\n')));
        return text.toString();
    }
}
