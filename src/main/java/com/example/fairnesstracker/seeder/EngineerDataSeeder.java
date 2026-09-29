package com.example.fairnesstracker.seeder;

import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.entity.Team;
import com.example.fairnesstracker.repository.EngineerRepository;
import com.example.fairnesstracker.repository.TeamRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Demo data for local development; enable with SEED_DEMO_DATA=true
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class EngineerDataSeeder {

    private final EngineerRepository engineerRepository;
    private final TeamRepository teamRepository;

    public EngineerDataSeeder(EngineerRepository engineerRepository, TeamRepository teamRepository) {
        this.engineerRepository = engineerRepository;
        this.teamRepository = teamRepository;
    }

    @PostConstruct
    public void seed() {

        if (engineerRepository.count() > 0) {
            return;
        }

        saveEngineer("Biradar Srikanth", "PEA88GO", "23r21a3309@mlrit.ac.in", "Platform");
        saveEngineer("Shanmukha", "PEAT42T", "shanmukha@mlrit.ac.in", "SRE");
        saveEngineer("Rahul", "PLJ25U6", "rahul@mlrit.ac.in", "Infrastructure");
        saveEngineer("Prithvi", "PXRH2AD", "prithvi@mlrit.ac.in", "Platform");
        saveEngineer("Saikiran", "PB153Q3", "pavan@mlrit.ac.in", "SRE");

        log.info("Seeded demo engineers");
    }

    private void saveEngineer(String name, String pagerDutyId, String email, String teamName) {
        Team team = teamRepository.findByName(teamName)
                .orElseGet(() -> teamRepository.save(new Team(teamName, Team.DEFAULT_TIMEZONE)));

        Engineer engineer = new Engineer();
        engineer.setName(name);
        engineer.setPagerDutyUserId(pagerDutyId);
        engineer.setEmail(email);
        engineer.setTeam(team);

        engineerRepository.save(engineer);
    }
}
