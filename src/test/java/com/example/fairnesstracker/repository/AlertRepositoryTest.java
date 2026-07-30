package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.entity.Engineer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
class AlertRepositoryTest {

    @Autowired
    private AlertRepository repo;

    @Autowired
    private EngineerRepository engineerRepository;

    @Test
    void saveAndFind_works() {

        Engineer engineer = new Engineer();
        engineer.setName("Test Engineer");
        engineer.setEmail("test@gmail.com");
        engineer.setTeam("Platform");

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
    void findByEngineerId_works() {

        Engineer engineer = new Engineer();
        engineer.setName("Srikanth");
        engineer.setEmail("srikanth@gmail.com");
        engineer.setTeam("Platform");

        engineer = engineerRepository.save(engineer);

        AlertEvent alert = new AlertEvent();
        alert.setEngineer(engineer);
        alert.setSeverity("P1");
        alert.setTriggeredAt(LocalDateTime.now());

        repo.save(alert);

        var alerts =
                repo.findByEngineer_Id(engineer.getId());

        assertEquals(1, alerts.size());
    }
}