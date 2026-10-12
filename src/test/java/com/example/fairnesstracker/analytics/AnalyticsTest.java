package com.example.fairnesstracker.analytics;

import com.example.fairnesstracker.security.JwtService;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Analytics on real PostgreSQL (the view and the SQL are PostgreSQL's), with a fake scorer that records what
 * it's asked. Every expected figure is worked out by hand from the fixture below.
 *
 * <p>Teams SRE (Asia/Kolkata: Asha, Bala) and EU (Europe/London: Emma). Window: September 2026 in India
 * time; the previous window is 2-31 August. UTC times, with what they are locally:
 * <pre>
 *  1 Asha  Checkout P1  Sep 25 13:00 (Fri 18:30, after hours)  ack 6 min, resolved 90 min, escalated
 *  2 Asha  Checkout P3  Sep 23 05:00 (Wed 10:30)               ack 10,     resolved 30
 *  3 Bala  Search   P2  Sep 27 04:30 (Sun 10:00, weekend)      no ack,     resolved 60
 *  4 Emma  Search   P1  Sep 10 09:00 (Thu 10:00 London)       ack 4,      resolved 20
 *  6 -     -        -   Sep 15 12:00 (Tue 12:00 UTC: no team)  open
 *  7 Asha  Search   P4  Aug 31 19:00 (Tue Sep 1 00:30: night)  open; September in India time
 *  5 Bala  Checkout P3  Aug 15 06:30 (Sat 12:00, weekend)      resolved 40; the previous window
 * </pre>
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "scorer.max-retries=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AnalyticsTest {

    private static final String[] SEPTEMBER = {"start", "2026-09-01", "end", "2026-09-30"};

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static MockWebServer scorer = new MockWebServer();
    static final List<String> scorerCalls = new CopyOnWriteArrayList<>();
    static volatile boolean scorerDown;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppUserRepository users;
    @Autowired private JwtService jwtService;

    private String admin;
    private String sreManager;
    private String bothTeamsManager;
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
        scorerCalls.clear();
        scorerDown = false;
        if (jdbc.queryForObject("SELECT count(*) FROM team", Long.class) == 0) {
            jdbc.execute("""
                    INSERT INTO team (id, name, timezone) VALUES (1, 'SRE', 'Asia/Kolkata'), (2, 'EU', 'Europe/London');
                    INSERT INTO engineer_data (id, email, name, team_id) VALUES
                      (1, 'asha@x.io', 'Asha', 1), (2, 'bala@x.io', 'Bala', 1), (3, 'emma@x.io', 'Emma', 2);
                    INSERT INTO service (id, pagerduty_service_id, name) VALUES (1, 'S1', 'Checkout'), (2, 'S2', 'Search');
                    INSERT INTO alert_event (id, engineer_id, service_id, severity, triggered_at, acknowledged_at, resolved_at) VALUES
                      (1, 1, 1, 'P1', '2026-09-25 13:00', '2026-09-25 13:06', '2026-09-25 14:30'),
                      (2, 1, 1, 'P3', '2026-09-23 05:00', '2026-09-23 05:10', '2026-09-23 05:30'),
                      (3, 2, 2, 'P2', '2026-09-27 04:30', NULL, '2026-09-27 05:30'),
                      (4, 3, 2, 'P1', '2026-09-10 09:00', '2026-09-10 09:04', '2026-09-10 09:20'),
                      (5, 2, 1, 'P3', '2026-08-15 06:30', NULL, '2026-08-15 07:10'),
                      (6, NULL, NULL, NULL, '2026-09-15 12:00', NULL, NULL),
                      (7, 1, 2, 'P4', '2026-08-31 19:00', NULL, NULL);
                    INSERT INTO alert_assignment (alert_id, engineer_id, kind, assigned_at, source) VALUES
                      (1, 2, 'ESCALATED', '2026-09-25 13:10', 'LOG');
                    """);
        }
        admin = token("admin@x.io", Role.ADMIN, null, List.of());
        sreManager = token("sre.manager@x.io", Role.MANAGER, null, List.of(1L));
        bothTeamsManager = token("both.manager@x.io", Role.MANAGER, null, List.of(1L, 2L));
        asha = token("asha.login@x.io", Role.ENGINEER, 1L, List.of());
    }

    // --- KPIs ----------------------------------------------------------------------------------------

    @Test
    void kpisForEverythingAnAdminSees() throws Exception {
        // Current: 1, 2, 3, 4, 6, 7. Previous: 5
        call(admin, "/api/v1/analytics/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.start").value("2026-09-01"))
                .andExpect(jsonPath("$.previousWindow.start").value("2026-08-02"))
                .andExpect(jsonPath("$.previousWindow.end").value("2026-08-31"))
                .andExpect(jsonPath("$.alerts.value").value(6.0))
                .andExpect(jsonPath("$.alerts.previous").value(1.0))
                .andExpect(jsonPath("$.alerts.changePct").value(500.0))
                // (6 + 10 + 4) / 3; nothing acknowledged before
                .andExpect(jsonPath("$.mttaMinutes.value").value(6.7))
                .andExpect(jsonPath("$.mttaMinutes.previous").isEmpty())
                .andExpect(jsonPath("$.mttaMinutes.changePct").isEmpty())
                // (90 + 30 + 60 + 20) / 4 against 40
                .andExpect(jsonPath("$.mttrMinutes.value").value(50.0))
                .andExpect(jsonPath("$.mttrMinutes.changePct").value(25.0))
                // 1, 3 and 7 of six; the one previous alert was on a Saturday
                .andExpect(jsonPath("$.afterHoursPct.value").value(50.0))
                .andExpect(jsonPath("$.afterHoursPct.changePct").value(-50.0))
                // Only 1 escalated; nothing to compare a change from 0 with
                .andExpect(jsonPath("$.escalationRatePct.value").value(16.7))
                .andExpect(jsonPath("$.escalationRatePct.changePct").isEmpty());
    }

    @Test
    void eachRoleCountsOnlyWhatItSees() throws Exception {
        // SRE: 1, 2, 3, 7. Asha: 1, 2, 7. EU: 4
        call(sreManager, "/api/v1/analytics/summary")
                .andExpect(jsonPath("$.alerts.value").value(4.0))
                .andExpect(jsonPath("$.escalationRatePct.value").value(25.0))
                .andExpect(jsonPath("$.afterHoursPct.value").value(75.0));
        call(asha, "/api/v1/analytics/summary").andExpect(jsonPath("$.alerts.value").value(3.0));
        call(admin, "/api/v1/analytics/summary", "team", "EU").andExpect(jsonPath("$.alerts.value").value(1.0));

        call(sreManager, "/api/v1/analytics/summary", "team", "EU").andExpect(status().isForbidden());
        call(asha, "/api/v1/analytics/heatmap", "team", "EU").andExpect(status().isForbidden());
        call(admin, "/api/v1/analytics/summary", "team", "Nope").andExpect(status().isBadRequest());
    }

    // --- Distributions -------------------------------------------------------------------------------

    @Test
    void alertsOverTimeHasEveryWeekAndTheSeverities() throws Exception {
        // Weeks from Monday in India time; alert 7 (Sep 1, 00:30) opens the first one
        call(admin, "/api/v1/analytics/alerts/timeseries", "granularity", "week")
                .andExpect(jsonPath("$.points.length()").value(5))
                .andExpect(jsonPath("$.points[0].start").value("2026-08-31"))
                .andExpect(jsonPath("$.points[0].bySeverity.p4").value(1))
                .andExpect(jsonPath("$.points[1].bySeverity.p1").value(1))
                .andExpect(jsonPath("$.points[2].bySeverity.other").value(1))
                .andExpect(jsonPath("$.points[3].total").value(3))
                .andExpect(jsonPath("$.points[3].bySeverity.p1").value(1))
                .andExpect(jsonPath("$.points[3].bySeverity.p2").value(1))
                .andExpect(jsonPath("$.points[3].bySeverity.p3").value(1))
                .andExpect(jsonPath("$.points[4].total").value(0));
        // A month is daily by default
        call(admin, "/api/v1/analytics/alerts/timeseries")
                .andExpect(jsonPath("$.granularity").value("day"))
                .andExpect(jsonPath("$.points.length()").value(30));
    }

    @Test
    void heatmapUsesEachTeamsLocalTime() throws Exception {
        String body = call(admin, "/api/v1/analytics/heatmap").andExpect(jsonPath("$.cells.length()").value(168))
                .andExpect(jsonPath("$.max").value(1))
                .andReturn().getResponse().getContentAsString();
        // dayOfWeek-hour of every alert: Fri 18 (1), Wed 10 (2), Sun 10 (3), Thu 10 London (4), Tue 12 UTC (6),
        // Tue 0 India (7)
        assertThat(com.jayway.jsonpath.JsonPath.<List<Object>>read(body, "$.cells[?(@.count > 0)]")).hasSize(6);
        assertThat(cell(body, 5, 18)).isEqualTo(1);
        assertThat(cell(body, 4, 10)).isEqualTo(1);
        assertThat(cell(body, 2, 0)).isEqualTo(1);
        assertThat(cell(body, 2, 12)).isEqualTo(1);
    }

    @Test
    void noisiestServicesFirst() throws Exception {
        call(admin, "/api/v1/analytics/services/top")
                .andExpect(jsonPath("$.services[0].name").value("Search"))
                .andExpect(jsonPath("$.services[0].alertCount").value(3))
                // 7 is still open: (60 + 20) / 2
                .andExpect(jsonPath("$.services[0].mttrMinutes").value(40.0))
                .andExpect(jsonPath("$.services[0].afterHoursPct").value(66.7))
                .andExpect(jsonPath("$.services[1].name").value("Checkout"))
                .andExpect(jsonPath("$.services[1].mttrMinutes").value(60.0))
                .andExpect(jsonPath("$.services[2].name").value("No service"))
                .andExpect(jsonPath("$.services[2].mttrMinutes").isEmpty());
    }

    // --- Scores --------------------------------------------------------------------------------------

    @Test
    void scoresComeFromTheScorerForTheRightTeamAndWindow() throws Exception {
        call(sreManager, "/api/v1/analytics/summary")
                .andExpect(jsonPath("$.scores.available").value(true))
                // Three decimals, as the scorer gives them
                .andExpect(jsonPath("$.gini.value").value(0.412))
                .andExpect(jsonPath("$.gini.previous").value(0.206))
                .andExpect(jsonPath("$.gini.changePct").value(100.0))
                .andExpect(jsonPath("$.fairnessLabel").value("MODERATE"))
                .andExpect(jsonPath("$.highRiskEngineers.value").value(1.0));

        // The manager's only team, this window and the previous one
        assertThat(scorerCalls).contains(
                "/v1/scores/fairness?start=2026-09-01&end=2026-09-30&team=SRE",
                "/v1/scores/fairness?start=2026-08-02&end=2026-08-31&team=SRE",
                "/v1/scores/burnout?start=2026-09-01&end=2026-09-30&team=SRE");
    }

    @Test
    void anEngineerSeesTheTeamsScoresButOnlyTheirOwnRows() throws Exception {
        call(asha, "/api/v1/analytics/snapshot")
                .andExpect(jsonPath("$.summary.gini.value").value(0.412))
                .andExpect(jsonPath("$.summary.highRiskEngineers").isEmpty())
                .andExpect(jsonPath("$.loadShare.length()").value(1))
                .andExpect(jsonPath("$.loadShare[0].name").value("Asha"))
                .andExpect(jsonPath("$.burnout.length()").value(1))
                .andExpect(jsonPath("$.burnout[0].name").value("Asha"))
                .andExpect(jsonPath("$.trend.periods.length()").value(2));
    }

    @Test
    void severalTeamsNeedAChoiceForScores() throws Exception {
        call(bothTeamsManager, "/api/v1/analytics/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts.value").value(5.0))
                .andExpect(jsonPath("$.scores.available").value(false))
                .andExpect(jsonPath("$.scores.reason").value("SEVERAL_TEAMS"))
                .andExpect(jsonPath("$.gini").isEmpty());
        assertThat(scorerCalls).isEmpty();

        call(bothTeamsManager, "/api/v1/analytics/summary", "team", "EU").andExpect(jsonPath("$.scores.available").value(true));
    }

    @Test
    void theRestStillWorksWhenTheScorerIsDown() throws Exception {
        scorerDown = true;

        call(admin, "/api/v1/analytics/snapshot")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.alerts.value").value(6.0))
                .andExpect(jsonPath("$.summary.scores.reason").value("SCORER_UNAVAILABLE"))
                .andExpect(jsonPath("$.trend.scores.available").value(false))
                .andExpect(jsonPath("$.loadShare").isEmpty());
    }

    @Test
    void windowRules() throws Exception {
        call(admin, "/api/v1/analytics/summary", "start", "2026-09-10", "end", "2026-09-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("end"));
        call(admin, "/api/v1/analytics/alerts/timeseries", "start", "2024-01-01", "end", "2026-01-01",
                "granularity", "day").andExpect(status().isBadRequest());
        call(admin, "/api/v1/analytics/summary", "granularity", "hour").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------------------------------

    private ResultActions call(String token, String path, String... extraParams) throws Exception {
        var request = get(path).header(HttpHeaders.AUTHORIZATION, token);
        for (int i = 0; i < SEPTEMBER.length; i += 2) {
            boolean overridden = false;
            for (int j = 0; j < extraParams.length; j += 2) {
                overridden |= extraParams[j].equals(SEPTEMBER[i]);
            }
            if (!overridden) request.param(SEPTEMBER[i], SEPTEMBER[i + 1]);
        }
        for (int i = 0; i < extraParams.length; i += 2) {
            request.param(extraParams[i], extraParams[i + 1]);
        }
        return mvc.perform(request);
    }

    private static int cell(String body, int day, int hour) {
        List<Integer> counts = com.jayway.jsonpath.JsonPath.read(body,
                "$.cells[?(@.dayOfWeek == " + day + " && @.hour == " + hour + ")].count");
        return counts.get(0);
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

    /** Answers like the scorer: Gini 0.412 this window, 0.206 before; Asha and Bala in every report. */
    static class FakeScorer extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            scorerCalls.add(request.getPath());
            if (scorerDown) {
                return new MockResponse().setResponseCode(503);
            }
            String path = request.getRequestUrl().encodedPath();
            boolean previous = "2026-08-02".equals(request.getRequestUrl().queryParameter("start"));
            String window = "{\"start\": \"2026-09-01T00:00:00+05:30\", \"end\": \"2026-10-01T00:00:00+05:30\", \"timezone\": \"Asia/Kolkata\"}";
            String body = switch (path) {
                case "/v1/scores/fairness" -> """
                        {"window": %s, "team": "SRE", "basis": "ACTIVE_ENGINEERS", "gini": %s, "label": "MODERATE",
                         "rotation_gini": null, "confidence": "LOW", "alert_count": 4, "engineers": [%s, %s]}"""
                        .formatted(window, previous ? "0.206" : "0.412", load(1, "Asha"), load(2, "Bala"));
                case "/v1/scores/burnout" -> """
                        {"window": %s, "team": "SRE", "engineers": [%s, %s]}"""
                        .formatted(window, burnout(1, "Asha", "HIGH"), burnout(2, "Bala", "LOW"));
                case "/v1/scores/trend" -> """
                        {"window": %s, "team": "SRE", "granularity": "week", "periods": [%s, %s]}"""
                        .formatted(window, period("2026-09-01"), period("2026-09-07"));
                default -> null;
            };
            return body == null ? new MockResponse().setResponseCode(404)
                    : new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
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

        private static String period(String start) {
            return """
                    {"start": "%sT00:00:00+05:30", "end": "%sT00:00:00+05:30", "alert_count": 1, "gini": 0.1,
                     "label": "FAIR", "confidence": "LOW", "average_burnout": 12.5, "high_risk_count": 0}"""
                    .formatted(start, start);
        }
    }
}
