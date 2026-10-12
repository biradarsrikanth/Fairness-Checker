package com.example.fairnesstracker.engineer.dto;

import com.example.fairnesstracker.engineer.Engineer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class EngineerDtos {

    private EngineerDtos() {
    }

    /** {@code team} is the name of an existing team (see /api/v1/teams). {@code active} defaults to true. */
    public record EngineerRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String team,
            @Size(max = 255) String pagerDutyUserId,
            Boolean active
    ) {
    }

    public record EngineerResponse(Long id, String name, String email, String team, String pagerDutyUserId,
                                   boolean active) {
        public static EngineerResponse from(Engineer engineer) {
            return new EngineerResponse(engineer.getId(), engineer.getName(), engineer.getEmail(),
                    engineer.getTeam().getName(), engineer.getPagerDutyUserId(), engineer.isActive());
        }
    }
}
