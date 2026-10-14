package com.example.fairnesstracker.report;

import com.example.fairnesstracker.security.JwtService;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reports on real PostgreSQL with a fake scorer: the files say what the analytics endpoints say, for what the
 * caller may see, and every download is recorded and audited. The fixture: September 2026 in India time;
 * team SRE (Asha, Bala) with alerts 1, 2, 3 and 7 in September and 5 in August; team EU (Emma) with alert 4.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "scorer.max-retries=0",
        "report.max-window-days=92"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ReportsTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static MockWebServer scorer = new MockWebServer();
    static volatile boolean scorerDown;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppUserRepository users;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private String admin;
    private String sreManager;
    private String asha;

    @BeforeAll
    static void startScorer() throws IOException {
        scorer.setDispatcher(new FakeScorer());
        scorer.start();
    }

    @AfterAll
    static void stopScorer() throws IOException {
        scorer.shutdown();
    }

    @DynamicPropertySource
    static void scorerUrl(DynamicPropertyRegistry registry) {
        registry.add("scorer.base-url", () -> scorer.url("/").toString());
    }

    @BeforeEach
    void seed() {
        scorerDown = false;
        if (jdbc.queryForObject("SELECT count(*) FROM team", Long.class) == 0) {
            jdbc.execute("""
                    INSERT INTO team (id, name, timezone) VALUES (1, 'SRE', 'Asia/Kolkata'), (2, 'EU', 'Europe/London');
                    INSERT INTO engineer_data (id, email, name, team_id) VALUES
                      (1, 'asha@x.io', 'Asha', 1), (2, 'bala@x.io', 'Bala', 1), (3, 'emma@x.io', 'Emma', 2);
                    INSERT INTO service (id, pagerduty_service_id, name) VALUES (1, 'S1', 'Checkout'), (2, 'S2', 'Search');
                    INSERT INTO alert_event (id, engineer_id, service_id, severity, title, triggered_at, acknowledged_at, resolved_at) VALUES
                      (1, 1, 1, 'P1', 'Checkout down', '2026-09-25 13:00', '2026-09-25 13:06', '2026-09-25 14:30'),
                      (2, 1, 1, 'P3', 'Checkout slow', '2026-09-23 05:00', '2026-09-23 05:10', '2026-09-23 05:30'),
                      (3, 2, 2, 'P2', 'Search errors', '2026-09-27 04:30', NULL, '2026-09-27 05:30'),
                      (4, 3, 2, 'P1', 'Search down', '2026-09-10 09:00', '2026-09-10 09:04', '2026-09-10 09:20'),
                      (5, 2, 1, 'P3', 'Checkout slow', '2026-08-15 06:30', NULL, '2026-08-15 07:10'),
                      (7, 1, 2, 'P4', 'Search slow', '2026-08-31 19:00', NULL, NULL);
                    INSERT INTO alert_assignment (alert_id, engineer_id, kind, assigned_at, source) VALUES
                      (1, 2, 'ESCALATED', '2026-09-25 13:10', 'LOG');
                    INSERT INTO oncall_shift (engineer_id, escalation_level, starts_at, ends_at) VALUES
                      (1, 1, '2026-09-21 00:00', '2026-09-28 00:00'),
                      (3, 1, '2026-09-21 00:00', '2026-09-28 00:00');
                    """);
        }
        admin = token("admin@x.io", Role.ADMIN, null, List.of());
        sreManager = token("sre.manager@x.io", Role.MANAGER, null, List.of(1L));
        asha = token("asha.login@x.io", Role.ENGINEER, 1L, List.of());
    }

    @Test
    void workbookHasTheSameFiguresAsTheSummary() throws Exception {
        JsonNode summary = json(call(sreManager, "/api/v1/analytics/summary", "team", "SRE"));
        MvcResult result = call(sreManager, "/api/v1/reports/analytics", "format", "xlsx", "team", "SRE")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"fairness-analytics-sre-2026-09-01-to-2026-09-30.xlsx\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheet("Summary");
            assertThat(number(sheet, "Alerts", 1)).isEqualTo(summary.at("/alerts/value").asDouble()).isEqualTo(4);
            assertThat(number(sheet, "Alerts", 2)).isEqualTo(summary.at("/alerts/previous").asDouble());
            assertThat(number(sheet, "Time to acknowledge (minutes, average)", 1))
                    .isEqualTo(summary.at("/mttaMinutes/value").asDouble());
            assertThat(number(sheet, "Time to resolve (minutes, average)", 1))
                    .isEqualTo(summary.at("/mttrMinutes/value").asDouble());
            assertThat(number(sheet, "After hours (%)", 1)).isEqualTo(summary.at("/afterHoursPct/value").asDouble());
            assertThat(number(sheet, "Escalated (%)", 1)).isEqualTo(summary.at("/escalationRatePct/value").asDouble());
            assertThat(number(sheet, "Fairness (Gini, 0 = even)", 1)).isEqualTo(summary.at("/gini/value").asDouble());

            // SRE's September alerts, newest first, and only SRE's shift
            Sheet alerts = workbook.getSheet("Alerts");
            assertThat(alerts.getLastRowNum()).isEqualTo(4);
            assertThat(alerts.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Search errors");
            Sheet shifts = workbook.getSheet("On-call");
            assertThat(shifts.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Asha");
            assertThat(shifts.getRow(1).getCell(1).getStringCellValue()).isEqualTo("SRE");
            assertThat(shifts.getRow(2)).isNull();
            assertThat(workbook.getSheet("Time of day").getRow(2).getCell(0).getStringCellValue()).isEqualTo("Asha");
        }

        Map<String, Object> export = jdbc.queryForMap(
                "SELECT * FROM report_export ORDER BY id DESC LIMIT 1");
        assertThat(export).containsEntry("report_type", "ANALYTICS").containsEntry("format", "XLSX")
                .containsEntry("status", "SUCCEEDED").containsEntry("team_id", 1L)
                .containsEntry("size_bytes", (long) result.getResponse().getContentAsByteArray().length);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'REPORT_EXPORTED' "
                + "AND entity_id = ?", Long.class, String.valueOf(export.get("id")))).isEqualTo(1);
    }

    @Test
    void pdfReadsLikeTheSummary() throws Exception {
        byte[] file = call(admin, "/api/v1/reports/analytics", "format", "pdf", "team", "SRE")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(file)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            // 4 alerts against 1 in August; Gini 0.412 against 0.206
            assertThat(text).contains("Fairness report", "Covers SRE", "Alerts 4", "+300%", "0.41", "+0.21",
                    "Asha", "Checkout", "The latest 4 of 4");
        }
    }

    @Test
    void engineersGetOnlyTheirOwnReport() throws Exception {
        byte[] file = call(asha, "/api/v1/reports/engineers/1", "format", "pdf")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"fairness-engineer-asha-2026-09-01-to-2026-09-30.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(file)) {
            assertThat(new PDFTextStripper().getText(document).replaceAll("\\s+", " "))
                    .contains("Engineer report", "Asha · SRE", "What makes up the score");
        }
        call(asha, "/api/v1/reports/engineers/2", "format", "pdf").andExpect(status().isForbidden());
        call(asha, "/api/v1/reports/analytics", "format", "xlsx", "team", "EU").andExpect(status().isForbidden());
        call(sreManager, "/api/v1/reports/engineers/3", "format", "xlsx").andExpect(status().isForbidden());
        call(admin, "/api/v1/reports/engineers/99", "format", "xlsx").andExpect(status().isNotFound());
    }

    @Test
    void refusesWhatItCannotBuildWithoutRecordingIt() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM report_export", Long.class);
        call(admin, "/api/v1/reports/analytics", "format", "xlsx", "start", "2026-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("start"))
                .andExpect(jsonPath("$.errors[0].message").value("a report covers at most 92 days"));
        call(admin, "/api/v1/reports/analytics", "format", "docx").andExpect(status().isBadRequest());
        call(admin, "/api/v1/reports/analytics", "format", "pdf", "team", "Nope").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_export", Long.class)).isEqualTo(before);

        // A client that asked for the file still gets the error as problem details
        mvc.perform(get("/api/v1/reports/analytics").header(HttpHeaders.AUTHORIZATION, admin)
                        .header(HttpHeaders.ACCEPT, "application/pdf").param("format", "pdf").param("team", "Nope"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void withoutTheScorerBothReportsAreStillMadeAndSayWhy() throws Exception {
        scorerDown = true;
        byte[] file = call(admin, "/api/v1/reports/analytics", "format", "xlsx", "team", "SRE")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(new DataFormatter().formatCellValue(workbook.getSheet("Burnout").getRow(1).getCell(0)))
                    .startsWith("The scoring service didn't answer");
        }

        // The engineer's alerts and shifts come from the database: only the scores are missing
        byte[] workbookFile = call(admin, "/api/v1/reports/engineers/1", "format", "xlsx")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookFile))) {
            assertThat(workbook.getSheet("Alerts")).isNotNull();
            assertThat(workbook.getSheet("Time of day")).isNull();
        }
        Map<String, Object> made = jdbc.queryForMap("SELECT * FROM report_export ORDER BY id DESC LIMIT 1");
        assertThat(made).containsEntry("report_type", "ENGINEER").containsEntry("status", "SUCCEEDED")
                .containsEntry("engineer_id", 1L);
    }

    @Test
    void listsMyDownloadsNewestFirst() throws Exception {
        String manager = token("history.manager@x.io", Role.MANAGER, null, List.of(1L));
        call(manager, "/api/v1/reports/analytics", "format", "pdf", "team", "SRE").andExpect(status().isOk());
        call(manager, "/api/v1/reports/engineers/2", "format", "xlsx").andExpect(status().isOk());

        mvc.perform(get("/api/v1/reports/exports").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("ENGINEER"))
                .andExpect(jsonPath("$.content[0].engineerName").value("Bala"))
                .andExpect(jsonPath("$.content[0].format").value("XLSX"))
                .andExpect(jsonPath("$.content[1].team").value("SRE"))
                .andExpect(jsonPath("$.content[1].start").value("2026-09-01"))
                .andExpect(jsonPath("$.content[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content[1].sizeBytes").isNumber());
    }

    // --- Helpers --------------------------------------------------------------------------------------

    private ResultActions call(String token, String path, String... params) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, token);
        boolean hasStart = false;
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
            hasStart |= params[i].equals("start");
        }
        if (!hasStart) {
            request.param("start", "2026-09-01").param("end", "2026-09-30");
        }
        return mvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static double number(Sheet sheet, String label, int column) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && label.equals(new DataFormatter().formatCellValue(row.getCell(0)))) {
                return row.getCell(column).getNumericCellValue();
            }
        }
        throw new AssertionError("No row " + label);
    }

    private String token(String email, Role role, Long engineerId, List<Long> teamIds) {
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null) {
            jdbc.update("INSERT INTO app_user (email, password_hash, full_name, role, engineer_id) VALUES (?, '{noop}x', 'x', ?, ?)",
                    email, role.name(), engineerId);
            user = users.findByEmail(email).orElseThrow();
            for (Long teamId : teamIds) {
                jdbc.update("INSERT INTO user_team_access (user_id, team_id) VALUES (?, ?)", user.getId(), teamId);
            }
        }
        return "Bearer " + jwtService.issue(user).value();
    }

    /** Answers like the scorer: Gini 0.412 in September and 0.206 before; Asha and Bala in every report. */
    static class FakeScorer extends Dispatcher {

        private static final String WINDOW = """
                {"start": "2026-09-01T00:00:00+05:30", "end": "2026-10-01T00:00:00+05:30", "timezone": "Asia/Kolkata"}""";

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            if (scorerDown) {
                return new MockResponse().setResponseCode(503);
            }
            String path = request.getRequestUrl().encodedPath();
            boolean previous = "2026-08-02".equals(request.getRequestUrl().queryParameter("start"));
            String body = switch (path) {
                case "/v1/scores/fairness" -> """
                        {"window": %s, "team": "SRE", "basis": "ACTIVE_ENGINEERS", "gini": %s, "label": "MODERATE",
                         "rotation_gini": null, "confidence": "LOW", "alert_count": 4, "engineers": [%s, %s]}"""
                        .formatted(WINDOW, previous ? "0.206" : "0.412", load(1, "Asha"), load(2, "Bala"));
                case "/v1/scores/burnout" -> """
                        {"window": %s, "team": "SRE", "engineers": [%s, %s]}"""
                        .formatted(WINDOW, burnout(1, "Asha", "HIGH"), burnout(2, "Bala", "LOW"));
                case "/v1/scores/trend" -> """
                        {"window": %s, "team": "SRE", "granularity": "week", "periods": []}""".formatted(WINDOW);
                case "/v1/scores/timeofday" -> """
                        {"window": %s, "team": "SRE", "engineers": [
                          {"engineer_id": 2, "name": "Bala", "team": "SRE", "night": 0, "morning": 1, "afternoon": 0, "evening": 0},
                          {"engineer_id": 1, "name": "Asha", "team": "SRE", "night": 1, "morning": 1, "afternoon": 0, "evening": 1}]}"""
                        .formatted(WINDOW);
                default -> path.startsWith("/v1/scores/engineers/") ? engineer(path.substring(path.lastIndexOf('/') + 1)) : null;
            };
            return body == null ? new MockResponse().setResponseCode(404)
                    : new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
        }

        private static String engineer(String id) {
            String name = "1".equals(id) ? "Asha" : "Bala";
            return """
                    {"window": %s, "engineer": {"engineer_id": %s, "name": "%s", "team": "SRE"}, "active": true,
                     "burnout": %s, "team_share_pct": 50.0,
                     "time_of_day": {"night": 1, "morning": 1, "afternoon": 0, "evening": 1}}"""
                    .formatted(WINDOW, id, name, burnout(Integer.parseInt(id), name, "HIGH"));
        }

        private static String load(int id, String name) {
            return """
                    {"engineer_id": %d, "name": "%s", "team": "SRE", "alert_count": 2, "escalations_received": 0,
                     "load": 10.0, "share_pct": 50.0, "oncall_hours": null, "load_per_oncall_hour": null}"""
                    .formatted(id, name);
        }

        private static String burnout(int id, String name, String risk) {
            return """
                    {"engineer_id": %d, "name": "%s", "team": "SRE", "alert_count": 2, "escalations_received": 0,
                     "score": 70.0, "risk": "%s", "night_pct": 50.0, "evening_pct": 0.0, "weekend_pct": 50.0,
                     "trend_ratio": 1.0, "median_ack_minutes": null, "oncall_hours": null,
                     "components": {"exposure": 0.5, "relative_load": 0.5, "trend": 0.0}}"""
                    .formatted(id, name, risk);
        }
    }
}
