package com.example.fairnesstracker.seeder;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.repository.AlertRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;

@Component
public class AlertDataSeeder {

    @Autowired
    private AlertRepository repo;

    @PostConstruct
    public void seed() {

        if (repo.count() > 0) {
            return;
        }

        String[] engineerIds = {
                "ENG001",
                "ENG002",
                "ENG003",
                "ENG004",
                "ENG005",
                "ENG006"
        };

        String[] engineerNames = {
                "Srikanth",
                "Shanmukha",
                "Pavan",
                "Rahul",
                "Prithvi",
                "Saikiran"
        };

        // Srikanth gets 40%, Saikiran gets 5%
        int[] weights = {40, 20, 15, 10, 10, 5};

        String[] severities = {"P1", "P2", "P3"};

        Random rand = new Random();

        LocalDateTime start = LocalDateTime.now().minusMonths(6);

        for (int i = 0; i < 200; i++) {

            int engineerIndex = weightedRandom(weights, rand);

            AlertEvent alert = new AlertEvent();

            alert.setEngineerId(engineerIds[engineerIndex]);
            alert.setEngineerName(engineerNames[engineerIndex]);

            alert.setSeverity(
                    severities[rand.nextInt(severities.length)]
            );

            LocalDateTime triggeredAt =
                    start.plusHours(rand.nextInt(4320));

            alert.setTriggeredAt(triggeredAt);

            alert.setResolvedAt(
                    triggeredAt.plusMinutes(
                            rand.nextInt(180) + 1
                    )
            );

            repo.save(alert);
        }

        System.out.println("Seeded 200 Alert Events");
    }

    private int weightedRandom(int[] weights, Random rand) {

        int totalWeight = 0;

        for (int weight : weights) {
            totalWeight += weight;
        }

        int random = rand.nextInt(totalWeight);

        int cumulative = 0;

        for (int i = 0; i < weights.length; i++) {

            cumulative += weights[i];

            if (random < cumulative) {
                return i;
            }
        }

        return weights.length - 1;
    }
}