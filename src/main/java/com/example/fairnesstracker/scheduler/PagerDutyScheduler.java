package com.example.fairnesstracker.scheduler;

import com.example.fairnesstracker.service.OncallSyncService;
import com.example.fairnesstracker.service.PagerDutyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Assumes a single gateway instance; add a distributed lock (e.g. ShedLock) before scaling out
@Slf4j
@Component
@ConditionalOnProperty(name = "pagerduty.sync.enabled", havingValue = "true", matchIfMissing = true)
public class PagerDutyScheduler {

    private final PagerDutyService pagerDutyService;
    private final OncallSyncService oncallSyncService;

    public PagerDutyScheduler(PagerDutyService pagerDutyService, OncallSyncService oncallSyncService) {
        this.pagerDutyService = pagerDutyService;
        this.oncallSyncService = oncallSyncService;
    }

    @Scheduled(fixedRateString = "${pagerduty.sync-rate}")
    public void syncPagerDuty() {
        // Independent: a failure in one doesn't skip the other; both retry on the next run
        try {
            pagerDutyService.syncIncidents();
        } catch (Exception e) {
            log.error("Scheduled PagerDuty incident sync failed", e);
        }
        try {
            oncallSyncService.syncOncalls();
        } catch (Exception e) {
            log.error("Scheduled PagerDuty on-call sync failed", e);
        }
    }
}
