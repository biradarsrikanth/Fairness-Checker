package com.example.fairnesstracker.pagerduty.dto.incident;

import lombok.Data;

// GET /incidents/{id}
@Data
public class IncidentResponse {
    private PagerDutyIncident incident;
}
