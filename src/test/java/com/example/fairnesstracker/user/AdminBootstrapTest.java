package com.example.fairnesstracker.user;

import com.example.fairnesstracker.audit.AuditLogRepository;
import com.example.fairnesstracker.audit.AuditService;
import com.example.fairnesstracker.security.PasswordConfig;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@DataJpaTest
class AdminBootstrapTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final PasswordEncoder ENCODER = new PasswordConfig().passwordEncoder();
    private static final AdminBootstrapProperties CONFIGURED =
            new AdminBootstrapProperties("Priya@Example.com", "correct-horse-battery", "Priya");

    @Autowired private AppUserRepository users;
    @Autowired private AuditLogRepository auditLog;

    @Test
    void createsTheFirstAdminWhenThereAreNoUsers() {
        bootstrap(CONFIGURED).run(null);

        AppUser admin = users.findByEmail("priya@example.com").orElseThrow();
        assertThat(admin.getEmail()).isEqualTo("Priya@Example.com");
        assertThat(admin.getFullName()).isEqualTo("Priya");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}$2a$12$");
        assertThat(ENCODER.matches("correct-horse-battery", admin.getPasswordHash())).isTrue();
        assertThat(auditLog.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo(com.example.fairnesstracker.audit.AuditAction.USER_CREATED);
            assertThat(entry.getUserId()).isNull();
            assertThat(entry.getEntityId()).isEqualTo(admin.getId().toString());
            assertThat(entry.getDetails()).containsEntry("source", "ADMIN_BOOTSTRAP");
        });
    }

    @Test
    void runsOnlyOnce() {
        bootstrap(CONFIGURED).run(null);
        bootstrap(new AdminBootstrapProperties("other@example.com", "another-long-password", null)).run(null);

        assertThat(users.findAll()).extracting(AppUser::getEmail).containsExactly("Priya@Example.com");
    }

    @Test
    void doesNothingWhenAnyUserExists() {
        AppUser manager = new AppUser("manager@example.com", "Manager", Role.MANAGER);
        manager.setPassword("{noop}x", NOW);
        users.save(manager);

        bootstrap(CONFIGURED).run(null);

        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenNotConfigured() {
        bootstrap(new AdminBootstrapProperties("", "", "Administrator")).run(null);

        assertThat(users.count()).isZero();
    }

    @Test
    void defaultsTheName() {
        bootstrap(new AdminBootstrapProperties("admin@example.com", "correct-horse-battery", " ")).run(null);

        assertThat(users.findByEmail("admin@example.com").orElseThrow().getFullName()).isEqualTo("Administrator");
    }

    @Test
    void anotherInstanceWinningTheRaceIsNotAnError() {
        AppUserRepository racing = mock(AppUserRepository.class);
        when(racing.count()).thenReturn(0L);
        when(racing.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ux_app_user_email"));

        assertThatNoException().isThrownBy(() ->
                new AdminBootstrap(racing, ENCODER, CONFIGURED, mock(AuditService.class), Clock.fixed(NOW, ZoneOffset.UTC))
                        .run(null));
    }

    @Test
    void settingsAreCheckedAtStartup() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(CONFIGURED)).isEmpty();
        assertThat(validator.validate(new AdminBootstrapProperties(null, null, null))).isEmpty();
        assertThat(validator.validate(new AdminBootstrapProperties("admin@example.com", "", null)))
                .extracting(v -> v.getMessage())
                .containsExactly("set both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD, or neither");
        assertThat(validator.validate(new AdminBootstrapProperties("admin@example.com", "short", null)))
                .extracting(v -> v.getPropertyPath() + " " + v.getMessage())
                .containsExactly("passwordLongEnough must be at least 12 characters");
        assertThat(validator.validate(new AdminBootstrapProperties("not-an-email", "correct-horse-battery", null)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("email");
    }

    private AdminBootstrap bootstrap(AdminBootstrapProperties properties) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new AdminBootstrap(users, ENCODER, properties, new AuditService(auditLog, clock), clock);
    }
}
