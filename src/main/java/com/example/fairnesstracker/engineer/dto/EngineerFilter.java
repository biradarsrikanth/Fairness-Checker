package com.example.fairnesstracker.engineer.dto;

import jakarta.validation.constraints.Size;

/** Optional filters for the engineer list: the team's name, and whether they're still on the rotation. */
public record EngineerFilter(
        @Size(max = 255) String team,
        Boolean active
) {
}
