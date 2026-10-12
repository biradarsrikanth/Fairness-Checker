package com.example.fairnesstracker.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Brute-force protection. Per account: {@code maxFailedAttempts} wrong passwords in a row lock it for
 * {@code lockDuration}. Per client IP: at most {@code attemptsPerIpPerMinute} sign-in attempts, whichever
 * accounts they're for.
 */
@Validated
@ConfigurationProperties("auth.login")
public record LoginProperties(
        @Min(1) @Max(100) int maxFailedAttempts,
        @NotNull @DurationMin(minutes = 1) @DurationMax(hours = 24) Duration lockDuration,
        @Min(1) @Max(1000) int attemptsPerIpPerMinute
) {
}
