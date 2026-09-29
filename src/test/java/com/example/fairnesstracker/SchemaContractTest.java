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
import org.testcontainers.containers.PostgreSQLContainer;
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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

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

    private List<String> columns(String view) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = ? ORDER BY ordinal_position
                """, String.class, view);
    }
}
