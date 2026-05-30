package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@RestController
@RequestMapping("/api/alerts")
public class AlertEventController {

    private final AlertService alertService;

    @Autowired
    public AlertEventController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<AlertEvent> saveAlert(@Valid @RequestBody AlertEvent alertEvent){
        AlertEvent newAlert=alertService.saveAlert(alertEvent);
        return ResponseEntity.ok(alertEvent);
    }

    @GetMapping
    public List<AlertEvent> getAllEvents(){
        return alertService.getAllEvents();
    }

    @GetMapping("/{id}")
    public AlertEvent getById(@PathVariable Long id){
        return alertService.getById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteEvent(@PathVariable Long id){
        alertService.deleteEvent(id);
        return ResponseEntity.ok("Event SuccessFully Deleted!");
    }

    @GetMapping("/filter")
    public List<AlertEvent> getFilteredAlerts(

            @RequestParam(required = false)
            String engineerId,

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


