package com.example.fairnesstracker.repository;

import com.example.fairnesstracker.entity.Engineer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DataJpaTest
class EngineerRepositoryTest {

    @Autowired
    private EngineerRepository repo;

    @Test
    void saveAndFind_works() {

        Engineer engineer = new Engineer();

        engineer.setName("Srikanth");
        engineer.setEmail("srikanth@gmail.com");
        engineer.setTeam("Platform");

        Engineer saved = repo.save(engineer);

        assertNotNull(saved.getId());

        Engineer found = repo.findById(saved.getId())
                .orElseThrow();

        assertEquals("Srikanth", found.getName());
        assertEquals("srikanth@gmail.com", found.getEmail());
        assertEquals("Platform", found.getTeam());
    }
}