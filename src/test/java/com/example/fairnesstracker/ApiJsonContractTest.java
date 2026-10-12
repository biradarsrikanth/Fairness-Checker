package com.example.fairnesstracker;

import com.example.fairnesstracker.alert.AlertAssignment;
import com.example.fairnesstracker.alert.AlertAssignmentRepository;
import com.example.fairnesstracker.alert.AlertEvent;
import com.example.fairnesstracker.alert.AlertRepository;
import com.example.fairnesstracker.alert.MonitoredService;
import com.example.fairnesstracker.alert.MonitoredServiceRepository;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.oncall.OncallShift;
import com.example.fairnesstracker.oncall.OncallShiftRepository;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the exact JSON the API returns: field names, null handling and date formats (UTC instants with a
 * trailing Z, so clients never mistake them for local time). Framework upgrades
 * (e.g. a new Jackson major version) must not change it by accident; deliberate changes update the
 * expected JSON here in the same commit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiJsonContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;
    @Autowired private MonitoredServiceRepository services;
    @Autowired private AlertRepository alerts;
    @Autowired private AlertAssignmentRepository assignments;
    @Autowired private OncallShiftRepository shifts;

    private Team team;
    private Engineer asha;
    private AlertEvent resolved;
    private AlertEvent bare;
    private OncallShift shift;

    @BeforeEach
    void seed() {
        team = teams.save(new Team("Contract SRE", "Europe/London"));

        asha = new Engineer();
        asha.setName("Asha");
        asha.setEmail("asha.contract@example.com");
        asha.setTeam(team);
        asha.setPagerDutyUserId("PCONTRACT1");
        asha = engineers.save(asha);

        MonitoredService payments = new MonitoredService();
        payments.setPagerdutyServiceId("SCONTRACT1");
        payments.setName("Payments API");
        payments = services.save(payments);

        resolved = new AlertEvent();
        resolved.setPagerDutyIncidentId("QCONTRACT1");
        resolved.setIncidentNumber(4242);
        resolved.setTitle("Database failover");
        resolved.setStatus(AlertEvent.STATUS_RESOLVED);
        resolved.setSeverity("P2");
        resolved.setUrgency("high");
        resolved.setSource("API_SYNC");
        resolved.setTriggeredAt(LocalDateTime.of(2026, 9, 27, 18, 0));
        resolved.setAcknowledgedAt(LocalDateTime.of(2026, 9, 27, 18, 5, 30));
        resolved.setResolvedAt(LocalDateTime.of(2026, 9, 27, 19, 0));
        resolved.setService(payments);
        resolved.setPagerDutyUserId("PCONTRACT1");
        resolved.setAssignedEngineerName("Asha");
        resolved.setEngineer(asha);
        resolved = alerts.save(resolved);

        // Only the required fields: everything optional must come back as an explicit null
        bare = new AlertEvent();
        bare.setTriggeredAt(LocalDateTime.of(2026, 9, 28, 3, 15));
        bare = alerts.save(bare);

        assignments.save(new AlertAssignment(resolved, asha, "PCONTRACT1", AlertAssignment.Kind.PAGED,
                LocalDateTime.of(2026, 9, 27, 18, 0), AlertAssignment.Source.LOG));
        assignments.save(new AlertAssignment(resolved, null, "PEXTERNAL", AlertAssignment.Kind.ESCALATED,
                LocalDateTime.of(2026, 9, 27, 18, 15), AlertAssignment.Source.LOG));

        shift = new OncallShift();
        shift.setEngineer(asha);
        shift.setStartsAt(LocalDateTime.of(2026, 9, 1, 3, 30));
        shift.setEndsAt(LocalDateTime.of(2026, 9, 8, 3, 30));
        shift = shifts.save(shift);
    }

    @Test
    void team() throws Exception {
        mvc.perform(get("/api/v1/teams/{id}", team.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id": %d, "name": "Contract SRE", "timezone": "Europe/London"}
                        """.formatted(team.getId()), JsonCompareMode.STRICT));
    }

    @Test
    void engineer() throws Exception {
        mvc.perform(get("/api/v1/engineers/{id}", asha.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id": %d, "name": "Asha", "email": "asha.contract@example.com", "team": "Contract SRE",
                         "pagerDutyUserId": "PCONTRACT1", "active": true}
                        """.formatted(asha.getId()), JsonCompareMode.STRICT));
    }

    @Test
    void alertWithEveryField() throws Exception {
        mvc.perform(get("/api/v1/alerts/{id}", resolved.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id": %d, "engineerId": %d, "engineerName": "Asha", "team": "Contract SRE",
                         "severity": "P2", "status": "resolved", "urgency": "high",
                         "triggeredAt": "2026-09-27T18:00:00Z", "acknowledgedAt": "2026-09-27T18:05:30Z",
                         "resolvedAt": "2026-09-27T19:00:00Z", "pagerDutyIncidentId": "QCONTRACT1",
                         "incidentNumber": 4242, "title": "Database failover", "serviceName": "Payments API",
                         "assignedEngineerName": "Asha", "source": "API_SYNC"}
                        """.formatted(resolved.getId(), asha.getId()), JsonCompareMode.STRICT));
    }

    @Test
    void alertWithOnlyRequiredFields() throws Exception {
        mvc.perform(get("/api/v1/alerts/{id}", bare.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id": %d, "engineerId": null, "engineerName": null, "team": null, "severity": null,
                         "status": "triggered", "urgency": null, "triggeredAt": "2026-09-28T03:15:00Z",
                         "acknowledgedAt": null, "resolvedAt": null, "pagerDutyIncidentId": null,
                         "incidentNumber": null, "title": null, "serviceName": null,
                         "assignedEngineerName": null, "source": null}
                        """.formatted(bare.getId()), JsonCompareMode.STRICT));
    }

    @Test
    void pagingHistory() throws Exception {
        mvc.perform(get("/api/v1/alerts/{id}/assignments", resolved.getId()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"kind": "PAGED", "engineerId": %d, "engineerName": "Asha",
                          "pagerDutyUserId": "PCONTRACT1", "assignedAt": "2026-09-27T18:00:00Z", "source": "LOG"},
                         {"kind": "ESCALATED", "engineerId": null, "engineerName": null,
                          "pagerDutyUserId": "PEXTERNAL", "assignedAt": "2026-09-27T18:15:00Z", "source": "LOG"}]
                        """.formatted(asha.getId()), JsonCompareMode.STRICT));
    }

    @Test
    void oncallShifts() throws Exception {
        mvc.perform(get("/api/v1/oncall")
                        .param("from", "2026-09-01").param("to", "2026-09-30")
                        .param("engineerId", asha.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id": %d, "engineerId": %d, "engineerName": "Asha", "escalationLevel": 1,
                          "startsAt": "2026-09-01T03:30:00Z", "endsAt": "2026-09-08T03:30:00Z"}]
                        """.formatted(shift.getId(), asha.getId()), JsonCompareMode.STRICT));
    }

    @Test
    void errorBodyAndRequestId() throws Exception {
        mvc.perform(get("/api/v1/teams/{id}", 999_999).header("X-Request-ID", "contract-test-1"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-ID", "contract-test-1"))
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Team 999999 not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/teams/999999"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.requestId").value("contract-test-1"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z")));
    }
}
