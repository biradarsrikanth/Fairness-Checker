package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.dto.pagerDuty.incident.PagerDutyResponse;
import com.example.fairnesstracker.dto.pagerDuty.user.PagerDutyUsersResponse;
import com.example.fairnesstracker.service.PagerDutyService;
import org.springframework.beans.factory.annotation.Autowired;
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
}