package com.example.fairnesstracker.user.dto;

import com.example.fairnesstracker.team.dto.TeamDtos.TeamRef;
import com.example.fairnesstracker.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    @Schema(description = "ENGINEER needs engineerId (an engineer without a login); only MANAGER takes teamIds")
    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String fullName,
            @NotNull Role role,
            @Positive @Nullable Long engineerId,
            @Nullable Set<@NotNull @Positive Long> teamIds) {
    }

    @Schema(description = "Team access is changed separately (PUT /users/{id}/teams); leaving MANAGER clears it")
    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String fullName,
            @NotNull Role role,
            @Positive @Nullable Long engineerId,
            @NotNull Boolean enabled) {
    }

    public record TeamAccessRequest(@NotNull Set<@NotNull @Positive Long> teamIds) {
    }

    /** Optional filters: role, enabled, and {@code q} matching email or name (case-insensitive). */
    public record UserFilter(Role role, Boolean enabled, @Size(max = 255) String q) {
    }

    @Schema(description = "A login as admins see it. lockedUntil: set while locked after failed sign-ins")
    public record UserAccountResponse(Long id, String email, String fullName, Role role, @Nullable Long engineerId,
                                      @Nullable String engineerName, List<TeamRef> teams, boolean enabled,
                                      boolean mustChangePassword, @Nullable Instant lockedUntil, @Nullable Instant lastLoginAt,
                                      @Nullable Instant passwordChangedAt) {
    }

    @Schema(description = "Shown once: give it to the user, who must change it at first sign-in")
    public record TemporaryPasswordResponse(UserAccountResponse user, String temporaryPassword) {
    }
}
