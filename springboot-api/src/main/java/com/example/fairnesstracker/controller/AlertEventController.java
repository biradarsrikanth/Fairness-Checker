package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.alert.AlertRequest;
import com.example.fairnesstracker.dto.alert.AlertResponse;
import com.example.fairnesstracker.entity.AlertEvent;
import com.example.fairnesstracker.service.AlertService;
import com.example.fairnesstracker.service.EngineerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final EngineerService engineerService;

    @Autowired
    public AlertEventController(AlertService alertService, EngineerService engineerService) {
        this.alertService = alertService;
        this.engineerService = engineerService;
    }

    @PostMapping
    public ResponseEntity<AlertEvent> saveAlert(@Valid @RequestBody AlertRequest request){
        AlertEvent newAlert=alertService.saveAlert(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newAlert);
    }

    @GetMapping
    public List<AlertEvent> getAllEvents(){
        return alertService.getAllEvents();
    }


    @GetMapping("/engineer/{engineerId}")
    public ResponseEntity<List<AlertResponse>> getAlertsByEngineer(
            @PathVariable Long engineerId) {

        return ResponseEntity.ok(
                alertService.getAlertsByEngineer(engineerId)
        );
    }

    @GetMapping("/{id}")
    public AlertEvent getById(@PathVariable Long id) {
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


