package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse;
import com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse;
import com.example.fairnesstracker.service.PagerDutyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pagerduty")
public class PagerDutyController {

    private final PagerDutyService pagerDutyService;

    @Autowired
    public PagerDutyController(PagerDutyService pagerDutyService) {
        this.pagerDutyService = pagerDutyService;
    }

    @GetMapping("/incidents")
    public PagerDutyResponse getIncidents() {
        return pagerDutyService.getIncidents();
    }

    @GetMapping("/users")
    public PagerDutyUsersResponse getUsers() {
        return pagerDutyService.getUsers();
    }


    @PostMapping("/sync")
    public String syncIncidents() {
        pagerDutyService.syncIncidents();
        return "Sync Complete";
    }

    @PostMapping("/resolve/{incidentId}")
    public ResponseEntity<String> resolveIncident(
            @PathVariable String incidentId,
            @RequestParam String email) {

        try {

            pagerDutyService.resolveIncident(incidentId,email);

            return ResponseEntity.ok(
                    "Incident resolved successfully"
            );

        } catch (Exception e) {

            return ResponseEntity
                    .internalServerError()
                    .body(e.getMessage());
        }
    }
}