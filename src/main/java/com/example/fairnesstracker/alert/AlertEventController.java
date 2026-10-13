package com.example.fairnesstracker.alert;

import com.example.fairnesstracker.security.AccessPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.fairnesstracker.alert.dto.AlertFilter;
import com.example.fairnesstracker.alert.dto.AlertRequest;
import com.example.fairnesstracker.alert.dto.AlertResponse;
import com.example.fairnesstracker.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Alerts", description = "Stored alerts and their paging history")
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertEventController {

    private final AlertService alertService;

    private final AccessPolicy access;

    public AlertEventController(AlertService alertService, AccessPolicy access) {
        this.alertService = alertService;
        this.access = access;
    }

    @Operation(operationId = "createAlert")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<AlertResponse> saveAlert(@Valid @RequestBody AlertRequest request) {
        access.requireManagedEngineer(request.engineerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.saveAlert(request));
    }

    @Operation(operationId = "listAlerts", summary = "List alerts",
            description = "Filters combine with AND; start and end are inclusive days in the analytics timezone. "
                    + "Sort by triggeredAt (default, newest first), resolvedAt, severity or status.")
    @GetMapping
    public PageResponse<AlertResponse> list(
            @Valid @ParameterObject AlertFilter filter,
            @ParameterObject @SortDefault(sort = "triggeredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return alertService.list(filter, pageable, access.scope());
    }

    @Operation(operationId = "getAlert")
    @GetMapping("/{id}")
    public AlertResponse getById(@PathVariable Long id) {
        access.requireAlert(id);
        return alertService.getById(id);
    }

    // Paging history: who was paged, escalated to or reassigned, in order
    @Operation(operationId = "listAlertAssignments")
    @GetMapping("/{id}/assignments")
    public List<AlertResponse.Assignment> getAssignments(@PathVariable Long id) {
        access.requireAlert(id);
        return alertService.getAssignments(id);
    }

    @Operation(operationId = "deleteAlert")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        alertService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }
}
