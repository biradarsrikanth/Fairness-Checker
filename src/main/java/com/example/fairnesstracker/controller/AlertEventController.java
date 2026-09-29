package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.alert.AlertRequest;
import com.example.fairnesstracker.dto.alert.AlertResponse;
import com.example.fairnesstracker.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@RestController
@RequestMapping("/api/alerts")
public class AlertEventController {

    private final AlertService alertService;

    public AlertEventController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> saveAlert(@Valid @RequestBody AlertRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.saveAlert(request));
    }

    @GetMapping
    public List<AlertResponse> getAllEvents(){
        return alertService.getAllEvents();
    }


    @GetMapping("/engineer/{engineerId}")
    public List<AlertResponse> getAlertsByEngineer(@PathVariable Long engineerId) {
        return alertService.getAlertsByEngineer(engineerId);
    }

    @GetMapping("/{id}")
    public AlertResponse getById(@PathVariable Long id) {
        return alertService.getById(id);
    }

    // Paging history: who was paged, escalated to or reassigned, in order
    @GetMapping("/{id}/assignments")
    public List<AlertResponse.Assignment> getAssignments(@PathVariable Long id) {
        return alertService.getAssignments(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id){
        alertService.deleteEvent(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public List<AlertResponse> getFilteredAlerts(

            @RequestParam(required = false)
            Long engineerId,

            @RequestParam(required = false)
            String severity,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {

        LocalDateTime fromDateTime =
                from != null ? from.atStartOfDay() : null;

        LocalDateTime toDateTime =
                to != null ? to.atTime(23,59,59) : null;

        return alertService.filterAlerts(
                engineerId,
                severity,
                fromDateTime,
                toDateTime
        );
    }
}
