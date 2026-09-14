package com.example.fairnesstracker.dto.scoring;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TimeOfDayCount(
        @JsonProperty("Night")
        Integer night,

        @JsonProperty("Morning")
        Integer morning,

        @JsonProperty("Afternoon")
        Integer afternoon,

        @JsonProperty("Evening")
        Integer evening
) {
}
