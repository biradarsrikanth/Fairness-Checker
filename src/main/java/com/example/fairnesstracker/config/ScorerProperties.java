package com.example.fairnesstracker.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// Checked at startup: a missing API key or URL stops the app instead of failing on the first request
@Validated
@ConfigurationProperties("scorer")
public record ScorerProperties(
        @NotBlank String baseUrl,
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @Min(0) @Max(5) int maxRetries
) {
}
