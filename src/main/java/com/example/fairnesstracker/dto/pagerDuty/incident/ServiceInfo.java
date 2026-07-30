package com.example.fairnesstracker.dto.pagerDuty.incident;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ServiceInfo {
    private String id;

    @JsonProperty("summary")
    private String name;
}

