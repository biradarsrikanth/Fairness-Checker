package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.pagerDuty.SyncResult;
import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse;
import com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse;
import com.example.fairnesstracker.service.OncallSyncService;
import com.example.fairnesstracker.service.PagerDutyService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pagerduty")
public class PagerDutyController {

    private final PagerDutyService pagerDutyService;
    private final OncallSyncService oncallSyncService;

    public PagerDutyController(PagerDutyService pagerDutyService, OncallSyncService oncallSyncService) {
        this.pagerDutyService = pagerDutyService;
        this.oncallSyncService = oncallSyncService;
    }

    @GetMapping("/incidents")
    public PagerDutyResponse getIncidents(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit) {
        return pagerDutyService.getIncidents(offset, limit);
    }

    @GetMapping("/users")
    public PagerDutyUsersResponse getUsers() {
        return pagerDutyService.getUsers();
    }

    @PostMapping("/sync")
    public SyncResult syncIncidents() {
        return pagerDutyService.syncIncidents();
    }

    // e.g. ?days=365 once to backfill a year of shifts; the scheduler keeps the recent ones current
    @PostMapping("/sync/oncalls")
    public SyncResult.Oncall syncOncalls(@RequestParam(defaultValue = "7") @Min(1) @Max(730) int days) {
        return oncallSyncService.syncOncalls(days);
    }

    // Errors go through GlobalExceptionHandler, which doesn't leak PagerDuty's response to the caller
    @PostMapping("/resolve/{incidentId}")
    public ResponseEntity<String> resolveIncident(@PathVariable String incidentId,
                                                  @RequestParam @Email String email) {
        pagerDutyService.resolveIncident(incidentId, email);
        return ResponseEntity.ok("Incident resolved successfully");
    }
}
