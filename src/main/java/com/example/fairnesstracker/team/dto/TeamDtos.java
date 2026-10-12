package com.example.fairnesstracker.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class TeamDtos {

    private TeamDtos() {
    }

    /** {@code timezone} is an IANA name such as Asia/Kolkata; defaults to Asia/Kolkata. */
    public record TeamRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 64) String timezone
    ) {
    }

    public record TeamResponse(Long id, String name, String timezone) {
    }

    /** A team named in another resource, e.g. the teams a user can see. */
    public record TeamRef(Long id, String name) {
    }
}
