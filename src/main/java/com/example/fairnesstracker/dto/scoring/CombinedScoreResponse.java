package com.example.fairnesstracker.dto.scoring;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record CombinedScoreResponse(
        Double gini,
        String label,

        @JsonProperty("engineer_share_pct")

        Map<String, Double> engineerSharePct
) {
}
