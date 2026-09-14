package com.example.fairnesstracker.dto.scoring;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BurnoutResponse(
        String engineer,
        Double score,
        String risk,

        @JsonProperty("off_hours_pct")
        Double offHoursPct,

        @JsonProperty("weekend_pct")
        Double weekendPct,

        Double trend
) {
}
