package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.fairnesstracker.pagerduty.dto.SyncResult;
import com.example.fairnesstracker.pagerduty.dto.incident.PagerDutyResponse;
import com.example.fairnesstracker.pagerduty.dto.user.PagerDutyUsersResponse;
import com.example.fairnesstracker.pagerduty.sync.SyncInProgressException;
import com.example.fairnesstracker.pagerduty.sync.SyncRun;
import com.example.fairnesstracker.pagerduty.sync.SyncRunner;
import io.swagger.v3.oas.annotations.Operation;
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
    private final SyncRunner syncRunner;
    private final AuditService audit;

    public PagerDutyController(PagerDutyService pagerDutyService, SyncRunner syncRunner, AuditService audit) {
        this.pagerDutyService = pagerDutyService;
        this.syncRunner = syncRunner;
        this.audit = audit;
    }

    private void requireConfigured() {
        if (!syncRunner.configured()) {
            throw new PagerDutyNotConfiguredException();
        }
    }

    @Operation(operationId = "listPagerDutyIncidents")
    @GetMapping("/incidents")
    public PagerDutyResponse getIncidents(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit) {
        requireConfigured();
        return pagerDutyService.getIncidents(offset, limit);
    }

    @Operation(operationId = "listPagerDutyUsers")
    @GetMapping("/users")
    public PagerDutyUsersResponse getUsers() {
        requireConfigured();
        return pagerDutyService.getUsers();
    }

    @Operation(operationId = "syncIncidents", summary = "Sync incidents now",
            description = "Recorded as a sync run. 409 SYNC_IN_PROGRESS while another incident sync is running, "
                    + "409 PAGERDUTY_NOT_CONFIGURED without an API token (nothing recorded).")
    @PostMapping("/sync")
    public SyncResult syncIncidents() {
        var run = syncRunner.syncIncidents(SyncRun.Trigger.MANUAL)
                .orElseThrow(() -> new SyncInProgressException(SyncRun.Kind.INCIDENTS));
        SyncResult result = run.result();
        audit.record(AuditAction.SYNC_TRIGGERED, "SYNC_RUN", run.runId(), Map.of("kind", "INCIDENTS",
                "created", result.created(), "updated", result.updated(), "failed", result.failed()));
        return result;
    }

    // e.g. ?days=365 once to backfill a year of shifts; the scheduler keeps the recent ones current
    @Operation(operationId = "syncOncalls", summary = "Sync on-call shifts now",
            description = "Recorded as a sync run. 409 SYNC_IN_PROGRESS while another on-call sync is running, "
                    + "409 PAGERDUTY_NOT_CONFIGURED without an API token (nothing recorded).")
    @PostMapping("/sync/oncalls")
    public SyncResult.Oncall syncOncalls(@RequestParam(defaultValue = "7") @Min(1) @Max(730) int days) {
        var run = syncRunner.syncOncalls(SyncRun.Trigger.MANUAL, days)
                .orElseThrow(() -> new SyncInProgressException(SyncRun.Kind.ONCALLS));
        SyncResult.Oncall result = run.result();
        audit.record(AuditAction.SYNC_TRIGGERED, "SYNC_RUN", run.runId(), Map.of("kind", "ONCALLS", "days", days,
                "shiftsStored", result.shiftsStored(), "skipped", result.skipped()));
        return result;
    }

    // Errors go through GlobalExceptionHandler, which doesn't leak PagerDuty's response to the caller
    @Operation(operationId = "resolvePagerDutyIncident")
    @PostMapping("/resolve/{incidentId}")
    public ResponseEntity<String> resolveIncident(@PathVariable String incidentId,
                                                  @RequestParam @Email String email) {
        requireConfigured();
        pagerDutyService.resolveIncident(incidentId, email);
        audit.record(AuditAction.INCIDENT_RESOLVED, "INCIDENT", incidentId, Map.of("asPagerDutyUser", email));
        return ResponseEntity.ok("Incident resolved successfully");
    }
}
