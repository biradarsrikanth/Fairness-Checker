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

        saveEngineer("Srikanth", "srikanth@gmail.com", "Platform");
        saveEngineer("Shanmukha", "shanmukha@gmail.com", "SRE");
        saveEngineer("Pavan", "pavan@gmail.com", "Backend");
        saveEngineer("Rahul", "rahul@gmail.com", "Infrastructure");
        saveEngineer("Prithvi", "prithvi@gmail.com", "Platform");
        saveEngineer("Saikiran", "saikiran@gmail.com", "SRE");

        System.out.println("Seeded Engineers");
    }

    private void saveEngineer(String name, String email, String team) {

        Engineer engineer = new Engineer();

        engineer.setName(name);
        engineer.setEmail(email);
        engineer.setTeam(team);

        engineerRepository.save(engineer);
    }
}