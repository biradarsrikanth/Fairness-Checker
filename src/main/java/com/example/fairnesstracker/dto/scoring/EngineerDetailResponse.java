package com.example.fairnesstracker.dto.scoring;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EngineerDetailResponse(
        String engineer,
        BurnoutResponse burnout,

        @JsonProperty("team_share_pct")
        Double teamSharePct
) {
}
