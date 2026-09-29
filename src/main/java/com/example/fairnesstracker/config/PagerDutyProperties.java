package com.example.fairnesstracker.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("pagerduty")
public record PagerDutyProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiToken,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout
) {
}
