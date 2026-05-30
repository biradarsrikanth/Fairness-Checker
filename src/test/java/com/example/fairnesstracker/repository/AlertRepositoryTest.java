package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.AlertEvent;
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

    @Test
    void saveAndFind_works() {

        AlertEvent alert = new AlertEvent();

        alert.setEngineerId("ENG009");
        alert.setEngineerName("Test");
        alert.setSeverity("P3");
        alert.setTriggeredAt(LocalDateTime.now());

        AlertEvent saved = repo.save(alert);

        assertNotNull(saved.getId());

        AlertEvent found = repo.findById(saved.getId())
                .orElseThrow();

        assertEquals(
                "Test",
                found.getEngineerName()
        );
    }
}