package com.example.fairnesstracker.common.web;

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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * List endpoints: page metadata, default order, filters, the size cap, allowed sort fields, and a fixed
 * number of SQL statements per page however many rows it has. No test transaction, so every request loads
 * its data the way production does.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginationTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;
    @Autowired private MonitoredServiceRepository services;
    @Autowired private AlertRepository alerts;
    @Autowired private AlertAssignmentRepository assignments;
    @Autowired private OncallShiftRepository shifts;

    private final List<Engineer> crew = new ArrayList<>();

    /**
     * Three teams with one engineer and one service each (the third engineer is inactive), 25 alerts on
     * 1–25 September at 10:00 UTC (engineer and service = day % 3, P1 on even days, P3 on odd ones), and
     * four on-call shifts around September.
     */
    @BeforeEach
    void seed() {
        List<MonitoredService> serviceList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Engineer engineer = new Engineer();
            engineer.setName("Engineer " + i);
            engineer.setEmail("pager" + i + "@example.com");
            engineer.setTeam(teams.save(new Team("Team " + i, "Asia/Kolkata")));
            engineer.setActive(i != 2);
            crew.add(engineers.save(engineer));

            MonitoredService service = new MonitoredService();
            service.setPagerdutyServiceId("SPAGE" + i);
            service.setName("Service " + i);
            serviceList.add(services.save(service));
        }
        for (int day = 1; day <= 25; day++) {
            AlertEvent alert = new AlertEvent();
            alert.setEngineer(crew.get(day % 3));
            alert.setService(serviceList.get(day % 3));
            alert.setSeverity(day % 2 == 0 ? "P1" : "P3");
            alert.setStatus(AlertEvent.STATUS_RESOLVED);
            alert.setTriggeredAt(LocalDateTime.of(2026, 9, day, 10, 0));
            alerts.save(alert);
        }
        shift(crew.get(1), LocalDateTime.of(2026, 8, 25, 0, 0), LocalDateTime.of(2026, 9, 1, 6, 0));
        shift(crew.get(0), LocalDateTime.of(2026, 9, 1, 6, 0), LocalDateTime.of(2026, 9, 8, 6, 0));
        shift(crew.get(0), LocalDateTime.of(2026, 9, 8, 6, 0), LocalDateTime.of(2026, 9, 15, 6, 0));
        shift(crew.get(1), LocalDateTime.of(2026, 10, 1, 6, 0), LocalDateTime.of(2026, 10, 8, 6, 0));
    }

    @AfterEach
    void cleanUp() {
        assignments.deleteAll();
        shifts.deleteAll();
        alerts.deleteAll();
        services.deleteAll();
        engineers.deleteAll();
        teams.deleteAll();
        crew.clear();
    }

    // --- Alerts --------------------------------------------------------------------------------------

    @Test
    void alertsComeNewestFirstWithPageMetadata() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.content[0].triggeredAt").value("2026-09-25T10:00:00Z"))
                .andExpect(jsonPath("$.content[9].triggeredAt").value("2026-09-16T10:00:00Z"));

        mvc.perform(get("/api/v1/alerts").param("size", "10").param("page", "2"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[4].triggeredAt").value("2026-09-01T10:00:00Z"));
    }

    @Test
    void alertFiltersCombine() throws Exception {
        // Team 0 = days divisible by 3; P1 = even days; up to the 15th → the 6th and the 12th
        mvc.perform(get("/api/v1/alerts").param("team", "Team 0").param("severity", "P1")
                        .param("start", "2026-09-01").param("end", "2026-09-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].triggeredAt").value("2026-09-12T10:00:00Z"))
                .andExpect(jsonPath("$.content[1].triggeredAt").value("2026-09-06T10:00:00Z"));

        mvc.perform(get("/api/v1/alerts").param("engineerId", crew.get(1).getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(9));
    }

    @Test
    void sortsByAnAllowedField() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("sort", "severity,asc").param("sort", "triggeredAt,asc")
                        .param("size", "1"))
                .andExpect(jsonPath("$.content[0].severity").value("P1"))
                .andExpect(jsonPath("$.content[0].triggeredAt").value("2026-09-02T10:00:00Z"));
    }

    @Test
    void rejectsOtherSortFields() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("sort", "title"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("can't sort by 'title'; use one of resolvedAt, severity, status, triggeredAt"));
    }

    @Test
    void capsThePageSize() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void rejectsAnEndBeforeTheStart() throws Exception {
        mvc.perform(get("/api/v1/alerts").param("start", "2026-09-10").param("end", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("end"));
    }

    // --- Engineers and on-call -----------------------------------------------------------------------

    @Test
    void engineersAreFilteredAndSortedByName() throws Exception {
        mvc.perform(get("/api/v1/engineers").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Engineer 0"))
                .andExpect(jsonPath("$.content[1].name").value("Engineer 1"));

        mvc.perform(get("/api/v1/engineers").param("team", "Team 2"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].active").value(false));
    }

    @Test
    void oncallShiftsOverlappingTheWindowInStartOrder() throws Exception {
        // The August shift ends inside September, so it overlaps; the October one doesn't
        mvc.perform(get("/api/v1/oncall").param("start", "2026-09-01").param("end", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].startsAt").value("2026-08-25T00:00:00Z"))
                .andExpect(jsonPath("$.content[2].startsAt").value("2026-09-08T06:00:00Z"));

        mvc.perform(get("/api/v1/oncall").param("start", "2026-09-01").param("end", "2026-09-30")
                        .param("engineerId", crew.get(0).getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // --- Query count ---------------------------------------------------------------------------------

    @Test
    void aPageCostsAtMostTwoQueriesWhateverItsSize() throws Exception {
        // One query for the rows (with their engineer, team and service) and one for the total
        assertThat(statementsFor(get("/api/v1/alerts").param("size", "25"))).isLessThanOrEqualTo(2);
        assertThat(statementsFor(get("/api/v1/engineers"))).isLessThanOrEqualTo(2);
        assertThat(statementsFor(get("/api/v1/oncall").param("start", "2026-08-01").param("end", "2026-10-31")))
                .isLessThanOrEqualTo(2);
    }

    private long statementsFor(MockHttpServletRequestBuilder request) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(request).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private void shift(Engineer engineer, LocalDateTime startsAt, LocalDateTime endsAt) {
        OncallShift shift = new OncallShift();
        shift.setEngineer(engineer);
        shift.setStartsAt(startsAt);
        shift.setEndsAt(endsAt);
        shifts.save(shift);
    }
}
