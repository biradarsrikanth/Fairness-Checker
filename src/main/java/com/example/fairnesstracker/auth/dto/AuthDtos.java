package com.example.fairnesstracker.auth.dto;

import com.example.fairnesstracker.team.dto.TeamDtos.TeamRef;
import com.example.fairnesstracker.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 200) String password) {
    }

    /** BCrypt reads at most 72 bytes, so longer passwords are refused rather than silently cut. */
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 72, message = "must be 12 to 72 characters") String newPassword) {
    }

    @Schema(description = "Send accessToken as 'Authorization: Bearer <accessToken>' until expiresAt, then call "
            + "/api/v1/auth/refresh (the refresh token travels in an httpOnly cookie)")
    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

        public TokenResponse(String accessToken, Instant expiresAt, UserResponse user) {
            this(accessToken, "Bearer", expiresAt, user);
        }
    }

    @Schema(description = "The signed-in user. teams: the teams they can see (every team for an ADMIN, the "
            + "managed ones for a MANAGER, their own for an ENGINEER)")
    public record UserResponse(Long id, String email, String fullName, Role role, @Nullable Long engineerId,
                               List<TeamRef> teams, boolean mustChangePassword) {
    }
}
