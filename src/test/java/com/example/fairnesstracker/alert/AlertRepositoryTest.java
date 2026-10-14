package com.example.fairnesstracker.alert;

import com.example.fairnesstracker.alert.dto.AlertFilter;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
class AlertRepositoryTest {

    @Autowired
    private AlertRepository repo;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EngineerRepository engineerRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Test
    void saveAndFind_works() {

        Engineer engineer = new Engineer();
        engineer.setName("Test Engineer");
        engineer.setEmail("test@gmail.com");
        engineer.setTeam(teamRepository.save(new Team("Platform", "Asia/Kolkata")));

        Engineer savedEngineer =
                engineerRepository.save(engineer);

        AlertEvent alert = new AlertEvent();

        alert.setEngineer(savedEngineer);
        alert.setSeverity("P3");
        alert.setTriggeredAt(LocalDateTime.now());

        AlertEvent saved = repo.save(alert);

        assertNotNull(saved.getId());

        AlertEvent found = repo.findById(saved.getId())
                .orElseThrow();

        assertEquals(
                "Test Engineer",
                found.getEngineer().getName()
        );

        assertEquals(
                "P3",
                found.getSeverity()
        );
    }

    @Test
    void filtersCombineAndTheEndDateIsInclusive() {
        Team platform = teamRepository.save(new Team("Platform", "Asia/Kolkata"));
        Team payments = teamRepository.save(new Team("Payments", "Asia/Kolkata"));
        Engineer asha = engineer("Asha", "asha@example.com", platform);
        Engineer bala = engineer("Bala", "bala@example.com", payments);

        AlertEvent matching = alert(asha, "P1", "resolved", LocalDateTime.of(2026, 9, 30, 23, 59));
        alert(asha, "P1", "resolved", LocalDateTime.of(2026, 10, 1, 0, 0));    // after the end date
        alert(asha, "P3", "resolved", LocalDateTime.of(2026, 9, 15, 12, 0));   // other severity
        alert(asha, "P1", "triggered", LocalDateTime.of(2026, 9, 15, 12, 0));  // other status
        alert(bala, "P1", "resolved", LocalDateTime.of(2026, 9, 15, 12, 0));   // other team
        alert(null, "P1", "resolved", LocalDateTime.of(2026, 9, 15, 12, 0));   // unattributed

        var filter = new AlertFilter(null, "Platform", "P1", "resolved",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        var page = repo.findAll(AlertSpecifications.matching(filter, ZoneOffset.UTC), PageRequest.of(0, 10));

        assertEquals(List.of(matching.getId()), page.map(AlertEvent::getId).getContent());
    }

    @Test
    void noFiltersMatchesEveryAlertIncludingUnattributedOnes() {
        alert(null, "P2", "triggered", LocalDateTime.of(2026, 9, 15, 12, 0));
        alert(engineer("Asha", "asha@example.com", teamRepository.save(new Team("Platform", "Asia/Kolkata"))),
                "P2", "triggered", LocalDateTime.of(2026, 9, 16, 12, 0));

        var everything = new AlertFilter(null, null, null, null, null, null, null);
        assertEquals(2, repo.findAll(AlertSpecifications.matching(everything, ZoneOffset.UTC), PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void searchesTitlesServicesAndIncidentNumbersIgnoringCase() {
        MonitoredService payments = new MonitoredService();
        payments.setName("Payments API");
        payments = serviceRepository.save(payments);

        AlertEvent byTitle = alert(null, "P2", "triggered", LocalDateTime.of(2026, 9, 15, 12, 0));
        byTitle.setTitle("Disk FULL on db-7");
        AlertEvent byService = alert(null, "P2", "triggered", LocalDateTime.of(2026, 9, 15, 13, 0));
        byService.setTitle("Latency spike");
        byService.setService(payments);
        AlertEvent byNumber = alert(null, "P2", "triggered", LocalDateTime.of(2026, 9, 15, 14, 0));
        byNumber.setTitle("Something else");
        byNumber.setIncidentNumber(4521);
        AlertEvent wildcard = alert(null, "P2", "triggered", LocalDateTime.of(2026, 9, 15, 15, 0));
        wildcard.setTitle("CPU at 100% on web-1");
        repo.saveAll(List.of(byTitle, byService, byNumber, wildcard));

        assertEquals(List.of(byTitle.getId()), search("disk full"));
        assertEquals(List.of(byService.getId()), search("PAYMENTS"));
        assertEquals(List.of(byNumber.getId()), search("#4521"));
        assertEquals(List.of(byNumber.getId()), search("4521"));
        // % and _ are taken literally, not as wildcards
        assertEquals(List.of(wildcard.getId()), search("100%"));
        assertEquals(List.of(), search("_"));
    }

    private List<Long> search(String q) {
        var filter = new AlertFilter(null, null, null, null, null, null, q);
        return repo.findAll(AlertSpecifications.matching(filter, ZoneOffset.UTC), PageRequest.of(0, 10))
                .map(AlertEvent::getId).getContent();
    }

    private Engineer engineer(String name, String email, Team team) {
        Engineer engineer = new Engineer();
        engineer.setName(name);
        engineer.setEmail(email);
        engineer.setTeam(team);
        return engineerRepository.save(engineer);
    }

    private AlertEvent alert(Engineer engineer, String severity, String status, LocalDateTime triggeredAt) {
        AlertEvent alert = new AlertEvent();
        alert.setEngineer(engineer);
        alert.setSeverity(severity);
        alert.setStatus(status);
        alert.setTriggeredAt(triggeredAt);
        return repo.save(alert);
    }
}
