package com.example.fairnesstracker.scheduler;

import com.example.fairnesstracker.service.PagerDutyService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PagerDutyScheduler {

    private final PagerDutyService pagerDutyService;

    public PagerDutyScheduler(
            PagerDutyService pagerDutyService) {

        this.pagerDutyService = pagerDutyService;
    }

    @Scheduled(fixedRateString = "${pagerduty.sync-rate}")
    public void syncPagerDutyIncidents() {
        try {
            System.out.println(
                    "Running PagerDuty Sync at "
                            + LocalDateTime.now());
            pagerDutyService.syncIncidents();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}