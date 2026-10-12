package com.example.fairnesstracker.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Access tokens: JWTs signed with HS256. Checked at startup: a missing or short secret stops the app
 * instead of issuing tokens anyone could forge. HS256 needs a key of at least 256 bits, hence 32 characters.
 */
@Validated
@ConfigurationProperties("auth.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        // Short: an access token can't be revoked, it can only expire
        @NotNull @DurationMin(minutes = 1) @DurationMax(hours = 1) Duration accessTokenTtl
) {
}
