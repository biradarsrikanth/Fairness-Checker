package com.example.fairnesstracker.common.config;

import com.example.fairnesstracker.FairnessCheckerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupChecksTest {

    @Test
    void theAppStartsWithoutAWebServerAsTheMigrationScriptRunsIt() {
        // ops/migrate-db.sh: --spring.main.web-application-type=none (and app.exit-after-startup, handled in main)
        SpringApplication app = new SpringApplication(FairnessCheckerApplication.class);

        try (ConfigurableApplicationContext context = app.run("--spring.profiles.active=test",
                "--spring.main.web-application-type=none")) {
            assertThat(context.isRunning()).isTrue();
        }
    }

    @Test
    void badSettingsStopTheAppBeforeFlywayTouchesTheDatabase() {
        // Flyway is on, against H2, where the PostgreSQL migrations fail: had it run first, its error would
        // be the one reported
        SpringApplication app = new SpringApplication(FairnessCheckerApplication.class);

        assertThatThrownBy(() -> app.run("--spring.profiles.active=test", "--server.port=0",
                "--spring.flyway.enabled=true", "--spring.jpa.hibernate.ddl-auto=validate",
                "--auth.jwt.secret=too-short"))
                .hasRootCauseInstanceOf(BindValidationException.class)
                .rootCause().hasMessageContaining("auth.jwt.secret");
    }
}
