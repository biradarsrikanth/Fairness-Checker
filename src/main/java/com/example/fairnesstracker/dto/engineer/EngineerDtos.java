package com.example.fairnesstracker.dto.engineer;

import com.example.fairnesstracker.entity.Engineer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class EngineerDtos {

    private EngineerDtos() {
    }

    /** {@code team} is the name of an existing team (see /api/teams). {@code active} defaults to true. */
    public record EngineerRequest(
            @NotBlank(message = "Name Cannot be Empty") @Size(max = 255) String name,
            @Email(message = "Email Not Valid!") @NotBlank(message = "Email is required") @Size(max = 255) String email,
            @NotBlank(message = "Team is required") String team,
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
