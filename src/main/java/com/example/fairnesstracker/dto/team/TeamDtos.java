package com.example.fairnesstracker.dto.team;

import com.example.fairnesstracker.entity.Team;
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
        public static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName(), team.getTimezone());
        }
    }
}
