package com.example.fairnesstracker.pagerduty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Without an API token PagerDuty is off: no scheduled sync, and the PagerDuty endpoints answer 409. */
@Validated
@ConfigurationProperties("pagerduty")
public record PagerDutyProperties(
        @NotBlank String baseUrl,
        @Nullable String apiToken,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout
) {

    public boolean configured() {
        return apiToken != null && !apiToken.isBlank();
    }
}
