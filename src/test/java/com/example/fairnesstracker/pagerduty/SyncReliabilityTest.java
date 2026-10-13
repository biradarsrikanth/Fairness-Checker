package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.pagerduty.sync.SyncRunner;
import com.example.fairnesstracker.security.JwtService;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sync runs, the sync lock and webhook de-duplication on real PostgreSQL (the unique event id, JSONB, the
 * shedlock table), against a fake PagerDuty that can be switched off.
 */
@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SyncReliabilityTest {

    // pagerduty.webhook.secret in application-test.properties
    private static final String SECRET = "test-secret";
    private static final String INCIDENT_LOCK = "pagerduty-sync-incidents";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static MockWebServer pagerDuty = new MockWebServer();
    static final List<String> pagerDutyCalls = new CopyOnWriteArrayList<>();
    static volatile boolean pagerDutyDown;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppUserRepository users;
    @Autowired private JwtService jwtService;
    @Autowired private SyncRunner syncRunner;
    @Autowired private LockProvider lockProvider;

    private String admin;
    private long adminId;

    @BeforeAll
    static void startPagerDuty() throws IOException {
        pagerDuty.setDispatcher(new FakePagerDuty());
        pagerDuty.start();
    }

    @AfterAll
    static void stopPagerDuty() throws IOException {
        pagerDuty.shutdown();
    }

    @DynamicPropertySource
    static void pagerDutyUrl(DynamicPropertyRegistry registry) {
        registry.add("pagerduty.base-url", () -> pagerDuty.url("/").toString());
    }

    @BeforeEach
    void reset() {
        pagerDutyCalls.clear();
        pagerDutyDown = false;
        jdbc.execute("""
                DELETE FROM webhook_event; DELETE FROM sync_run; DELETE FROM alert_assignment;
                DELETE FROM alert_event; DELETE FROM service;
                """);
        // Not the shedlock table: ShedLock remembers which lock rows exist and only updates them afterwards
        if (jdbc.queryForObject("SELECT count(*) FROM team", Long.class) == 0) {
            jdbc.execute("""
                    INSERT INTO team (id, name, timezone) VALUES (1, 'SRE', 'Asia/Kolkata');
                    INSERT INTO engineer_data (id, email, name, team_id, pager_duty_user_id)
                      VALUES (1, 'asha@x.io', 'Asha', 1, 'PASHA');
                    """);
        }
        admin = token("admin@x.io", Role.ADMIN);
        adminId = users.findByEmail("admin@x.io").orElseThrow().getId();
    }

    // --- Sync runs -----------------------------------------------------------------------------------

    @Test
    void manualSyncIsRecordedWithWhoStartedIt() throws Exception {
        call(admin, post("/api/v1/pagerduty/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
        call(admin, post("/api/v1/pagerduty/sync")).andExpect(jsonPath("$.updated").value(1));

        call(admin, get("/api/v1/pagerduty/sync-runs"))
                .andExpect(jsonPath("$.totalElements").value(2))
                // Newest first
                .andExpect(jsonPath("$.content[0].kind").value("INCIDENTS"))
                .andExpect(jsonPath("$.content[0].trigger").value("MANUAL"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.content[0].createdCount").value(0))
                .andExpect(jsonPath("$.content[0].updatedCount").value(1))
                .andExpect(jsonPath("$.content[0].triggeredBy").value(adminId))
                .andExpect(jsonPath("$.content[0].durationMs").isNumber())
                .andExpect(jsonPath("$.content[0].error").isEmpty())
                .andExpect(jsonPath("$.content[1].createdCount").value(1));

        long runId = jdbc.queryForObject("SELECT max(id) FROM sync_run", Long.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'SYNC_TRIGGERED' "
                + "AND entity_type = 'SYNC_RUN' AND entity_id = ?", Long.class, String.valueOf(runId))).isEqualTo(1);
    }

    @Test
    void failedSyncIsRecorded() throws Exception {
        pagerDutyDown = true;

        call(admin, post("/api/v1/pagerduty/sync"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
        // The scheduler logs failures instead of throwing; both kinds are recorded
        new PagerDutyScheduler(syncRunner).syncPagerDuty();

        call(admin, get("/api/v1/pagerduty/sync-runs").param("status", "FAILED"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].kind").value("ONCALLS"))
                .andExpect(jsonPath("$.content[0].trigger").value("SCHEDULED"))
                .andExpect(jsonPath("$.content[0].triggeredBy").isEmpty())
                .andExpect(jsonPath("$.content[0].finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.content[2].trigger").value("MANUAL"))
                .andExpect(jsonPath("$.content[2].error", containsString("500 Internal Server Error")));
        call(admin, get("/api/v1/pagerduty/sync-status"))
                .andExpect(jsonPath("$.incidents.running").value(false))
                .andExpect(jsonPath("$.incidents.lastRun.status").value("FAILED"))
                .andExpect(jsonPath("$.incidents.lastSuccess").isEmpty());

        pagerDutyDown = false;
        call(admin, post("/api/v1/pagerduty/sync")).andExpect(status().isOk());
        call(admin, get("/api/v1/pagerduty/sync-status"))
                .andExpect(jsonPath("$.incidents.lastRun.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.incidents.lastSuccess.id").isNumber())
                .andExpect(jsonPath("$.oncalls.lastRun.status").value("FAILED"));
    }

    @Test
    void onlyOneSyncOfAKindRunsAtATime() throws Exception {
        // Another instance holds the incident lock
        SimpleLock held = lockProvider.lock(new LockConfiguration(Instant.now(), INCIDENT_LOCK,
                Duration.ofMinutes(5), Duration.ZERO)).orElseThrow();

        try {
            call(admin, post("/api/v1/pagerduty/sync"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SYNC_IN_PROGRESS"));
            new PagerDutyScheduler(syncRunner).syncPagerDuty();

            // The incident sync didn't run at all; the on-call sync has its own lock and did
            assertThat(pagerDutyCalls).noneMatch(path -> path.startsWith("/incidents"));
            assertThat(pagerDutyCalls).anyMatch(path -> path.startsWith("/oncalls"));
            assertThat(jdbc.queryForList("SELECT kind FROM sync_run", String.class)).containsExactly("ONCALLS");
        } finally {
            held.unlock();
        }
        call(admin, post("/api/v1/pagerduty/sync")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sync_run WHERE kind = 'INCIDENTS'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void runAbandonedByADeadInstanceIsClosedByTheNextOne() throws Exception {
        jdbc.update("INSERT INTO sync_run (kind, trigger_type, status, started_at) "
                + "VALUES ('INCIDENTS', 'SCHEDULED', 'RUNNING', now() - interval '2 hours')");
        // Older than lockAtMostFor (30 minutes): no longer running
        call(admin, get("/api/v1/pagerduty/sync-status"))
                .andExpect(jsonPath("$.incidents.running").value(false))
                .andExpect(jsonPath("$.incidents.lastRun.status").value("RUNNING"));

        call(admin, post("/api/v1/pagerduty/sync")).andExpect(status().isOk());

        call(admin, get("/api/v1/pagerduty/sync-runs").param("trigger", "SCHEDULED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].error", startsWith("Interrupted")))
                .andExpect(jsonPath("$.content[0].finishedAt").isNotEmpty());
    }

    @Test
    void recentRunningRunIsReportedAsRunning() throws Exception {
        jdbc.update("INSERT INTO sync_run (kind, trigger_type, status, started_at) "
                + "VALUES ('ONCALLS', 'SCHEDULED', 'RUNNING', now() - interval '1 minute')");
        call(admin, get("/api/v1/pagerduty/sync-status"))
                .andExpect(jsonPath("$.oncalls.running").value(true))
                .andExpect(jsonPath("$.oncalls.lastRun.finishedAt").isEmpty())
                .andExpect(jsonPath("$.incidents.running").value(false))
                .andExpect(jsonPath("$.incidents.lastRun").isEmpty());
    }

    // --- Webhooks ------------------------------------------------------------------------------------

    @Test
    void redeliveredWebhookIsAppliedOnce() throws Exception {
        String triggered = event("EV1", "incident.triggered", "QWH1", "2026-09-27T18:00:00Z");
        String acknowledged = event("EV2", "incident.acknowledged", "QWH1", "2026-09-27T18:05:00Z");
        String resolved = event("EV3", "incident.resolved", "QWH1", "2026-09-27T18:30:00Z");

        webhook(triggered).andExpect(content().string("Created"));
        webhook(triggered).andExpect(status().isOk()).andExpect(content().string("Already processed"));
        webhook(acknowledged).andExpect(content().string("Updated"));
        webhook(resolved).andExpect(content().string("Updated"));
        // A late redelivery of the acknowledgement must not reopen the incident
        webhook(acknowledged).andExpect(content().string("Already processed"));

        assertThat(jdbc.queryForObject("SELECT status FROM alert_event WHERE pager_duty_incident_id = 'QWH1'",
                String.class)).isEqualTo("resolved");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_event", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_assignment", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT status FROM webhook_event ORDER BY id", String.class))
                .containsExactly("PROCESSED", "PROCESSED", "PROCESSED");
    }

    @Test
    void eventsWithoutAnIdAreDeduplicatedByTheirBody() throws Exception {
        String triggered = event(null, "incident.triggered", "QWH2", "2026-09-27T18:00:00Z");
        webhook(triggered).andExpect(content().string("Created"));
        webhook(triggered).andExpect(content().string("Already processed"));
        assertThat(jdbc.queryForObject("SELECT length(pagerduty_event_id) FROM webhook_event", Integer.class))
                .isEqualTo(64);

        String other = event(null, "service.updated", null, "2026-09-27T18:00:00Z");
        webhook(other).andExpect(content().string("Ignored"));
        assertThat(jdbc.queryForObject("SELECT status FROM webhook_event WHERE event_type = 'service.updated'",
                String.class)).isEqualTo("IGNORED");
    }

    @Test
    void failedWebhookIsRecordedAndAppliedOnPagerDutysRetry() throws Exception {
        // We missed the trigger, so the gateway reads the incident from PagerDuty, which is down
        String acknowledged = event("EV10", "incident.acknowledged", "QLATE", "2026-09-27T18:05:00Z");
        pagerDutyDown = true;
        webhook(acknowledged).andExpect(status().isBadGateway());

        Map<String, Object> failed = jdbc.queryForMap("SELECT status, error, incident_id FROM webhook_event");
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat((String) failed.get("error")).contains("500");
        assertThat(failed.get("incident_id")).isEqualTo("QLATE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_event", Long.class)).isZero();

        pagerDutyDown = false;
        webhook(acknowledged).andExpect(status().isOk()).andExpect(content().string("Fetched"));
        assertThat(jdbc.queryForObject("SELECT status FROM webhook_event", String.class)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT error FROM webhook_event", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_event WHERE pager_duty_incident_id = 'QLATE'",
                Long.class)).isEqualTo(1);
    }

    @Test
    void adminReplaysAFailedEvent() throws Exception {
        pagerDutyDown = true;
        webhook(event("EV20", "incident.resolved", "QLATE", "2026-09-27T19:00:00Z"));
        long id = jdbc.queryForObject("SELECT id FROM webhook_event", Long.class);

        call(admin, get("/api/v1/pagerduty/webhook-events").param("status", "FAILED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pagerdutyEventId").value("EV20"))
                .andExpect(jsonPath("$.content[0].eventType").value("incident.resolved"))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist());
        call(admin, get("/api/v1/pagerduty/webhook-events/{id}", id))
                .andExpect(jsonPath("$.payload.event.id").value("EV20"))
                .andExpect(jsonPath("$.payload.event.data.id").value("QLATE"));

        // Still down: fails again, and says why
        call(admin, post("/api/v1/pagerduty/webhook-events/{id}/replay", id)).andExpect(status().isBadGateway());
        pagerDutyDown = false;
        call(admin, post("/api/v1/pagerduty/webhook-events/{id}/replay", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.processedAt").isNotEmpty())
                .andExpect(jsonPath("$.error").isEmpty());
        call(admin, post("/api/v1/pagerduty/webhook-events/{id}/replay", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        call(admin, post("/api/v1/pagerduty/webhook-events/999999/replay")).andExpect(status().isNotFound());

        assertThat(jdbc.queryForList("SELECT details->>'status' FROM audit_log WHERE action = 'WEBHOOK_REPLAYED' "
                + "AND entity_id = ? ORDER BY id", String.class, String.valueOf(id)))
                .containsExactly("FAILED", "PROCESSED");
    }

    @Test
    void onlyAdminsSeeSyncActivity() throws Exception {
        String manager = token("manager@x.io", Role.MANAGER);
        for (String path : List.of("/api/v1/pagerduty/sync-runs", "/api/v1/pagerduty/sync-status",
                "/api/v1/pagerduty/webhook-events")) {
            call(manager, get(path)).andExpect(status().isForbidden());
        }
        call(manager, post("/api/v1/pagerduty/webhook-events/1/replay")).andExpect(status().isForbidden());
        call(admin, get("/api/v1/pagerduty/sync-runs").param("sort", "error,asc"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------------------------------

    private ResultActions call(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions webhook(String payload) throws Exception {
        return mvc.perform(post("/api/webhook/pagerduty").contentType(MediaType.APPLICATION_JSON)
                .content(payload).header("X-PagerDuty-Signature", sign(payload)));
    }

    private String token(String email, Role role) {
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null) {
            jdbc.update("INSERT INTO app_user (email, password_hash, full_name, role) VALUES (?, '{noop}x', 'x', ?)",
                    email, role.name());
            user = users.findByEmail(email).orElseThrow();
        }
        return "Bearer " + jwtService.issue(user).value();
    }

    private static String event(String id, String type, String incidentId, String occurredAt) {
        String data = incidentId == null ? "{\"id\": \"S9\", \"type\": \"service\"}" : """
                {"id": "%s", "type": "incident", "number": 7, "title": "Checkout latency", "status": "%s",
                 "urgency": "high", "created_at": "2026-09-27T18:00:00Z",
                 "service": {"id": "S1", "summary": "Checkout"},
                 "assignees": [{"id": "PASHA", "summary": "Asha"}]}"""
                .formatted(incidentId, type.substring("incident.".length()));
        return """
                {"event": {%s"event_type": "%s", "resource_type": "incident", "occurred_at": "%s", "data": %s}}"""
                .formatted(id != null ? "\"id\": \"" + id + "\", " : "", type, occurredAt, data);
    }

    private static String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v1=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** One incident (Q1, paged to Asha), no on-call shifts; QLATE when asked for by id. */
    static class FakePagerDuty extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath();
            pagerDutyCalls.add(path);
            if (pagerDutyDown) {
                return new MockResponse().setResponseCode(500);
            }
            String body;
            if (path.startsWith("/incidents?")) {
                body = "{\"incidents\": [" + incident("Q1") + "], \"more\": false}";
            } else if (path.startsWith("/incidents/QLATE?") || path.equals("/incidents/QLATE")) {
                body = "{\"incident\": " + incident("QLATE") + "}";
            } else if (path.contains("/log_entries")) {
                body = "{\"log_entries\": []}";
            } else if (path.startsWith("/oncalls")) {
                body = "{\"oncalls\": [], \"more\": false}";
            } else {
                return new MockResponse().setResponseCode(404);
            }
            return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
        }

        private static String incident(String id) {
            return """
                    {"id": "%s", "status": "resolved", "title": "CPU high", "urgency": "high",
                     "incident_number": 7, "created_at": "2026-09-27T18:00:00Z",
                     "resolved_at": "2026-09-27T19:00:00Z", "priority": {"summary": "P2"},
                     "service": {"id": "S1", "summary": "api"},
                     "assignments": [{"assignee": {"id": "PASHA", "summary": "Asha"}}]}""".formatted(id);
        }
    }
}
