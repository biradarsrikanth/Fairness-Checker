package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.pagerduty.sync.SyncRun.Trigger;
import com.example.fairnesstracker.pagerduty.sync.SyncRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Every instance schedules the syncs; SyncRunner's lock lets one at a time run each kind
@Slf4j
@Component
@ConditionalOnProperty(name = "pagerduty.sync.enabled", havingValue = "true", matchIfMissing = true)
public class PagerDutyScheduler {

    private final SyncRunner syncRunner;

    public PagerDutyScheduler(SyncRunner syncRunner) {
        this.syncRunner = syncRunner;
    }

    @Scheduled(fixedRateString = "${pagerduty.sync-rate}")
    public void syncPagerDuty() {
        if (!syncRunner.configured()) {
            return;
        }
        // Independent: a failure in one doesn't skip the other; both retry on the next run. Failures are also
        // recorded as sync runs
        try {
            syncRunner.syncIncidents(Trigger.SCHEDULED);
        } catch (Exception e) {
            log.error("Scheduled PagerDuty incident sync failed", e);
        }
        try {
            syncRunner.syncOncalls(Trigger.SCHEDULED);
        } catch (Exception e) {
            log.error("Scheduled PagerDuty on-call sync failed", e);
        }
    }
}
