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

        engineerRepository.save(
                new Engineer(
                        null,
                        "Srikanth",
                        "srikanth@gmail.com",
                        "Platform"
                )
        );

        engineerRepository.save(
                new Engineer(
                        null,
                        "Shanmukha",
                        "shanmukha@gmail.com",
                        "SRE"
                )
        );

        engineerRepository.save(
                new Engineer(
                        null,
                        "Pavan",
                        "pavan@gmail.com",
                        "Backend"
                )
        );

        engineerRepository.save(
                new Engineer(
                        null,
                        "Rahul",
                        "rahul@gmail.com",
                        "Infrastructure"
                )
        );

        engineerRepository.save(
                new Engineer(
                        null,
                        "Prithvi",
                        "prithvi@gmail.com",
                        "Platform"
                )
        );

        engineerRepository.save(
                new Engineer(
                        null,
                        "Saikiran",
                        "saikiran@gmail.com",
                        "SRE"
                )
        );

        System.out.println("Seeded Engineers");
    }
}