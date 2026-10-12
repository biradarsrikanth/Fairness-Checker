package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.fairnesstracker.pagerduty.dto.SyncResult;
import com.example.fairnesstracker.pagerduty.dto.incident.PagerDutyResponse;
import com.example.fairnesstracker.pagerduty.dto.user.PagerDutyUsersResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.Map;

@Tag(name = "PagerDuty", description = "Live PagerDuty data and manual syncs (admins only)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/pagerduty")
public class PagerDutyController {

    private final PagerDutyService pagerDutyService;
    private final OncallSyncService oncallSyncService;
    private final AuditService audit;

    public PagerDutyController(PagerDutyService pagerDutyService, OncallSyncService oncallSyncService,
                               AuditService audit) {
        this.pagerDutyService = pagerDutyService;
        this.oncallSyncService = oncallSyncService;
        this.audit = audit;
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
        SyncResult result = pagerDutyService.syncIncidents();
        audit.record(AuditAction.SYNC_TRIGGERED, "SYNC", null, Map.of("kind", "INCIDENTS",
                "created", result.created(), "updated", result.updated(), "failed", result.failed()));
        return result;
    }

    // e.g. ?days=365 once to backfill a year of shifts; the scheduler keeps the recent ones current
    @PostMapping("/sync/oncalls")
    public SyncResult.Oncall syncOncalls(@RequestParam(defaultValue = "7") @Min(1) @Max(730) int days) {
        SyncResult.Oncall result = oncallSyncService.syncOncalls(days);
        audit.record(AuditAction.SYNC_TRIGGERED, "SYNC", null, Map.of("kind", "ONCALLS", "days", days,
                "shiftsStored", result.shiftsStored(), "skipped", result.skipped()));
        return result;
    }

    // Errors go through GlobalExceptionHandler, which doesn't leak PagerDuty's response to the caller
    @PostMapping("/resolve/{incidentId}")
    public ResponseEntity<String> resolveIncident(@PathVariable String incidentId,
                                                  @RequestParam @Email String email) {
        pagerDutyService.resolveIncident(incidentId, email);
        audit.record(AuditAction.INCIDENT_RESOLVED, "INCIDENT", incidentId, Map.of("asPagerDutyUser", email));
        return ResponseEntity.ok("Incident resolved successfully");
    }
}
