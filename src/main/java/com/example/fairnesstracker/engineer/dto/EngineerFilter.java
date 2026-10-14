package com.example.fairnesstracker.engineer.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional filters for the engineer list: the team's name, whether they're still on the rotation, and {@code q},
 * which searches names and emails.
 */
public record EngineerFilter(
        @Size(max = 255) String team,
        Boolean active,
        @Size(max = 100) String q
) {
}
