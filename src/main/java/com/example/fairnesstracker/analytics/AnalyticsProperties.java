package com.example.fairnesstracker.analytics;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/**
 * {@code timezone}: how analytics dates are read. Must be the scorer's TEAM_TIMEZONE, so a window covers the
 * same hours in the gateway's counts and in the scorer's scores. Checked at startup.
 */
@Validated
@ConfigurationProperties("analytics")
public record AnalyticsProperties(@NotBlank String timezone) {

    public AnalyticsProperties {
        if (timezone != null && !timezone.isBlank()) {
            ZoneId.of(timezone);
        }
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }
}
