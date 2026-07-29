package com.example.fairnesstracker.dto.pagerDuty.incident;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PagerDutyIncident {

    private List<Assignment> assignments;

    @JsonProperty("last_status_change_by")
    private LastStatusChangeBy lastStatusChangeBy;

    private String id;

    private String title;

    private String status;

    private String urgency;

    @JsonProperty("incident_number")
    private Integer incidentNumber;

    private ServiceInfo service;
    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("resolved_at")
    private String resolvedAt;

    private Priority priority;
}