package com.example.fairnesstracker.dto.pagerDuty;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PagerDutyIncident {

    private String id;

    private String title;

    private String status;

    private String urgency;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("resolved_at")
    private String resolvedAt;
}