package com.example.fairnesstracker.dto.pagerDuty.incident;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PagerDutyIncident {

    private List<Assignment> assignments;

    private String id;

    private String title;

    private String status;

    private String urgency;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("resolved_at")
    private String resolvedAt;

    private Priority priority;
}