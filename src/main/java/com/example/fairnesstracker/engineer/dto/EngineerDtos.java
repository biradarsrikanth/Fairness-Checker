package com.example.fairnesstracker.engineer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public final class EngineerDtos {

    private EngineerDtos() {
    }

    /** {@code team} is the name of an existing team (see /api/v1/teams). {@code active} defaults to true. */
    public record EngineerRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String team,
            @Size(max = 255) @Nullable String pagerDutyUserId,
            @Nullable Boolean active
    ) {
    }

    /** {@code team} is the team's name. */
    public record EngineerResponse(Long id, String name, String email, String team, @Nullable String pagerDutyUserId,
                                   boolean active) {
    }
}
