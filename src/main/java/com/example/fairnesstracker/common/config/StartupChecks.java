package com.example.fairnesstracker.common.config;

import com.example.fairnesstracker.analytics.AnalyticsProperties;
import com.example.fairnesstracker.auth.LoginProperties;
import com.example.fairnesstracker.auth.RefreshCookieProperties;
import com.example.fairnesstracker.auth.RefreshTokenProperties;
import com.example.fairnesstracker.pagerduty.PagerDutyProperties;
import com.example.fairnesstracker.scoring.ScorerProperties;
import com.example.fairnesstracker.security.JwtProperties;
import com.example.fairnesstracker.user.AdminBootstrapProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway migrates the database as soon as its bean is built, before most other beans. Settings are bound
 * and validated when first used, so a missing JWT_SECRET would otherwise stop the app only after it had
 * changed the database. Flyway builds its customizers first; this one needs every validated settings
 * class, so bad settings stop the app before anything is migrated.
 */
@Configuration(proxyBeanMethods = false)
public class StartupChecks {

    @Bean
    public FlywayConfigurationCustomizer validateSettingsBeforeMigrating(
            JwtProperties jwt, RefreshTokenProperties refreshTokens, RefreshCookieProperties refreshCookie,
            LoginProperties login,
            AdminBootstrapProperties bootstrapAdmin,
            ScorerProperties scorer, PagerDutyProperties pagerDuty, AnalyticsProperties analytics) {
        return configuration -> {
        };
    }
}
