package com.example.fairnesstracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies every Flyway migration to a real PostgreSQL, then starts the app with
 * {@code ddl-auto=validate}: fails if the entities and the migrations disagree, if a scorer view loses a
 * column, or if a database constraint stops working. Skipped when Docker isn't available.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class SchemaContractTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void scorerViewsKeepTheirColumns() {
        assertThat(columns("scoring_engineer_v"))
                .containsExactly("engineer_id", "name", "team", "active", "team_id", "timezone");
        assertThat(columns("scoring_alert_v"))
                .containsExactly("alert_id", "engineer_id", "severity", "status", "triggered_at", "resolved_at",
                        "acknowledged_at", "triggered_at_local", "escalation_count");
        assertThat(columns("scoring_assignment_v")).containsExactly("alert_id", "engineer_id", "kind", "assigned_at");
        assertThat(columns("scoring_oncall_v"))
                .containsExactly("engineer_id", "escalation_level", "starts_at", "ends_at");
    }

    @Test
    void alertViewConvertsToTheTeamsTimezone() {
        long engineer = engineer("asha@example.com", team("EU", "Europe/London"));
        jdbc.update("INSERT INTO alert_event (triggered_at, engineer_id) VALUES ('2026-09-27 18:00', ?)", engineer);

        assertThat(jdbc.queryForObject("SELECT triggered_at_local::text FROM scoring_alert_v", String.class))
                .isEqualTo("2026-09-27 19:00:00");
    }

    @Test
    void constraintsRejectBadData() {
        long team = team("SRE", "Asia/Kolkata");
        engineer("asha@example.com", team);
        jdbc.update("INSERT INTO alert_event (pager_duty_incident_id, triggered_at) VALUES ('Q1', now())");

        assertRejected("INSERT INTO alert_event (pager_duty_incident_id, triggered_at) VALUES ('Q1', now())",
                DuplicateKeyException.class);
        assertRejected("INSERT INTO alert_event (triggered_at, status) VALUES (now(), 'bogus')",
                DataIntegrityViolationException.class);
        assertRejected("INSERT INTO alert_event (triggered_at, severity) VALUES (now(), 'P9')",
                DataIntegrityViolationException.class);
        assertRejected("INSERT INTO alert_event (triggered_at, resolved_at) VALUES (now(), now() - interval '1 hour')",
                DataIntegrityViolationException.class);
        // Emails are unique regardless of case
        assertRejected("INSERT INTO engineer_data (email, name, team_id) VALUES ('ASHA@example.com', 'x', " + team + ")",
                DuplicateKeyException.class);
        assertRejected("INSERT INTO engineer_data (email, name, team_id) VALUES ('new@example.com', 'x', 999)",
                DataIntegrityViolationException.class);
        assertRejected("INSERT INTO oncall_shift (engineer_id, starts_at, ends_at) "
                + "VALUES (1, now(), now() - interval '1 hour')", DataIntegrityViolationException.class);
        assertRejected("INSERT INTO alert_assignment (alert_id, kind, assigned_at, source) "
                + "VALUES (1, 'PAGED', now(), 'LOG')", DataIntegrityViolationException.class);
    }

    @Test
    void authConstraintsRejectBadData() {
        long team = team("SRE", "Asia/Kolkata");
        long engineer = engineer("asha@example.com", team);
        long admin = user("admin@example.com", "ADMIN", null);
        user("asha.login@example.com", "ENGINEER", engineer);

        // Logins are unique regardless of case
        assertRejected(userSql("ADMIN@example.com", "ADMIN", null), DuplicateKeyException.class);
        assertRejected(userSql("x@example.com", "OWNER", null), DataIntegrityViolationException.class);
        // An ENGINEER login must point at an engineer, and only one login per engineer
        assertRejected(userSql("x@example.com", "ENGINEER", null), DataIntegrityViolationException.class);
        assertRejected(userSql("x@example.com", "MANAGER", engineer), DuplicateKeyException.class);
        // An engineer with a login can't be deleted out from under it
        assertRejected("DELETE FROM engineer_data WHERE id = " + engineer, DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO refresh_token (user_id, token_hash, family_id, expires_at) "
                + "VALUES (?, repeat('a', 64), gen_random_uuid(), now() + interval '7 days')", admin);
        assertRejected("INSERT INTO refresh_token (user_id, token_hash, family_id, expires_at) VALUES ("
                + admin + ", repeat('a', 64), gen_random_uuid(), now() + interval '7 days')", DuplicateKeyException.class);
        assertRejected("INSERT INTO refresh_token (user_id, token_hash, family_id, expires_at) VALUES ("
                + admin + ", 'not-a-hash', gen_random_uuid(), now() + interval '7 days')",
                DataIntegrityViolationException.class);
        assertRejected("INSERT INTO refresh_token (user_id, token_hash, family_id, expires_at) VALUES ("
                + admin + ", repeat('b', 64), gen_random_uuid(), now() - interval '1 second')",
                DataIntegrityViolationException.class);
    }

    @Test
    void deletingAUserRemovesItsTokensAndTeamAccess() {
        long team = team("SRE", "Asia/Kolkata");
        long manager = user("manager@example.com", "MANAGER", null);
        jdbc.update("INSERT INTO user_team_access (user_id, team_id) VALUES (?, ?)", manager, team);
        jdbc.update("INSERT INTO refresh_token (user_id, token_hash, family_id, expires_at) "
                + "VALUES (?, repeat('c', 64), gen_random_uuid(), now() + interval '7 days')", manager);

        jdbc.update("DELETE FROM app_user WHERE id = ?", manager);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_team_access", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token", Long.class)).isZero();
    }

    @Test
    void theAuditLogIsAppendOnly() {
        long entry = jdbc.queryForObject("INSERT INTO audit_log (action, details) VALUES ('LOGIN_SUCCEEDED', "
                + "'{\"ip\": \"x\"}'::jsonb) RETURNING id", Long.class);

        assertRejected("UPDATE audit_log SET action = 'X' WHERE id = " + entry, Exception.class);
        assertRejected("DELETE FROM audit_log WHERE id = " + entry, Exception.class);
        assertThat(jdbc.queryForObject("SELECT details->>'ip' FROM audit_log WHERE id = ?", String.class, entry))
                .isEqualTo("x");
    }

    @Test
    void syncWebhookAndReportConstraints() {
        long admin = user("admin@example.com", "ADMIN", null);
        jdbc.update("INSERT INTO webhook_event (pagerduty_event_id, event_type, status, payload) "
                + "VALUES ('E1', 'incident.triggered', 'RECEIVED', '{}')");

        // The same PagerDuty event is stored once
        assertRejected("INSERT INTO webhook_event (pagerduty_event_id, event_type, status, payload) "
                + "VALUES ('E1', 'incident.triggered', 'RECEIVED', '{}')", DuplicateKeyException.class);
        assertRejected("INSERT INTO webhook_event (pagerduty_event_id, event_type, status, payload) "
                + "VALUES ('E2', 'x', 'DONE', '{}')", DataIntegrityViolationException.class);
        assertRejected("INSERT INTO sync_run (kind, trigger_type, status) VALUES ('USERS', 'MANUAL', 'RUNNING')",
                DataIntegrityViolationException.class);
        assertRejected("INSERT INTO sync_run (kind, trigger_type, status, finished_at) "
                + "VALUES ('INCIDENTS', 'MANUAL', 'FAILED', now() - interval '1 hour')", DataIntegrityViolationException.class);
        assertRejected("INSERT INTO report_export (user_id, report_type, format, window_start, window_end, status) "
                + "VALUES (" + admin + ", 'ANALYTICS', 'CSV', '2026-09-01', '2026-09-30', 'RUNNING')",
                DataIntegrityViolationException.class);
        assertRejected("INSERT INTO report_export (user_id, report_type, format, window_start, window_end, status) "
                + "VALUES (" + admin + ", 'ANALYTICS', 'PDF', '2026-09-30', '2026-09-01', 'RUNNING')",
                DataIntegrityViolationException.class);
    }

    @Test
    void deletingAUserKeepsTheirHistory() {
        long admin = user("admin@example.com", "ADMIN", null);
        jdbc.update("INSERT INTO audit_log (user_id, actor_email, action) VALUES (?, 'admin@example.com', 'USER_CREATED')",
                admin);
        jdbc.update("INSERT INTO sync_run (kind, trigger_type, status, triggered_by) VALUES ('INCIDENTS', 'MANUAL', 'SUCCEEDED', ?)",
                admin);

        jdbc.update("DELETE FROM app_user WHERE id = ?", admin);

        assertThat(jdbc.queryForObject("SELECT actor_email FROM audit_log", String.class)).isEqualTo("admin@example.com");
        assertThat(jdbc.queryForObject("SELECT triggered_by IS NULL FROM sync_run", Boolean.class)).isTrue();
    }

    // PostgreSQL aborts the whole transaction on an error; a savepoint keeps the test's transaction usable
    private void assertRejected(String sql, Class<? extends Exception> expected) {
        jdbc.execute("SAVEPOINT check_constraint");
        assertThatThrownBy(() -> jdbc.update(sql)).as(sql).isInstanceOf(expected);
        jdbc.execute("ROLLBACK TO SAVEPOINT check_constraint");
    }

    @Test
    void auditColumnsAreFilledByTheDatabase() {
        long team = team("SRE", "Asia/Kolkata");
        assertThat(jdbc.queryForObject("SELECT created_at IS NOT NULL AND updated_at IS NOT NULL FROM team WHERE id = ?",
                Boolean.class, team)).isTrue();
    }

    private long team(String name, String timezone) {
        return jdbc.queryForObject("INSERT INTO team (name, timezone) VALUES (?, ?) RETURNING id",
                Long.class, name, timezone);
    }

    private long engineer(String email, long team) {
        return jdbc.queryForObject("INSERT INTO engineer_data (email, name, team_id) VALUES (?, 'x', ?) RETURNING id",
                Long.class, email, team);
    }

    private long user(String email, String role, Long engineer) {
        return jdbc.queryForObject(userSql(email, role, engineer) + " RETURNING id", Long.class);
    }

    private static String userSql(String email, String role, Long engineer) {
        return "INSERT INTO app_user (email, password_hash, full_name, role, engineer_id) VALUES ('%s', '{noop}x', 'x', '%s', %s)"
                .formatted(email, role, engineer);
    }

    private List<String> columns(String view) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = ? ORDER BY ordinal_position
                """, String.class, view);
    }
}
