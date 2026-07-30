package com.example.fairnesstracker.seeder;

import com.example.fairnesstracker.entity.Engineer;
import com.example.fairnesstracker.repository.EngineerRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EngineerDataSeeder {

    @Autowired
    private EngineerRepository engineerRepository;

    @PostConstruct
    public void seed() {

        if (engineerRepository.count() > 0) {
            return;
        }

        saveEngineer(
                "Biradar Srikanth",
                "PEA88GO",
                "23r21a3309@mlrit.ac.in",
                "Platform"
        );

        saveEngineer(
                "Shanmukha",
                "PEAT42T",
                "shanmukha@mlrit.ac.in",
                "SRE"
        );

        saveEngineer(
                "Rahul",
                "PLJ25U6",
                "rahul@mlrit.ac.in",
                "Infrastructure"
        );

        saveEngineer(
                "Prithvi",
                "PXRH2AD",
                "prithvi@mlrit.ac.in",
                "Platform"
        );

        saveEngineer(
                "Saikiran",
                "PB153Q3",
                "pavan@mlrit.ac.in",
                "SRE"
        );

        System.out.println("Seeded Engineers");
    }

    private void saveEngineer(
            String name,
            String pagerDutyId,
            String email,
            String team) {

        Engineer engineer = new Engineer();

        engineer.setName(name);
        engineer.setPagerDutyUserId(pagerDutyId);
        engineer.setEmail(email);
        engineer.setTeam(team);

        engineerRepository.save(engineer);
    }
}