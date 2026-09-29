package com.example.fairnesstracker.dto.pagerDuty.incident;

import lombok.Data;

// GET /incidents/{id}
@Data
public class IncidentResponse {
    private PagerDutyIncident incident;
}
