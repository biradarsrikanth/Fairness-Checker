package com.example.fairnesstracker.auth;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param ttl        how long a refresh token lasts; each refresh issues a new one with a fresh lifetime, so a
 *                   session ends after this long without use
 * @param reuseGrace how long after rotating a token it may still be presented without counting as theft:
 *                   covers two tabs refreshing at the same moment with the same cookie
 */
@Validated
@ConfigurationProperties("auth.refresh-token")
public record RefreshTokenProperties(
        @NotNull @DurationMin(hours = 1) @DurationMax(days = 90) Duration ttl,
        @NotNull @DurationMin(seconds = 0) @DurationMax(seconds = 60) Duration reuseGrace
) {
}
