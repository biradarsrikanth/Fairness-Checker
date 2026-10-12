package com.example.fairnesstracker.user;

import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/**
 * There's no sign-up: admins create users. The first admin comes from ADMIN_BOOTSTRAP_EMAIL and
 * ADMIN_BOOTSTRAP_PASSWORD, only while there are no users at all, and has to choose a new password at first
 * sign-in. Once anyone exists this does nothing, so the variables can (and should) be removed.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;
    private final AuditService audit;
    private final Clock clock;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder,
                          AdminBootstrapProperties properties, AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("No users exist yet and nobody can sign in: set ADMIN_BOOTSTRAP_EMAIL and "
                    + "ADMIN_BOOTSTRAP_PASSWORD to create the first admin");
            return;
        }

        AppUser admin = new AppUser(properties.email(), fullName(), Role.ADMIN);
        admin.setPassword(passwordEncoder.encode(properties.password()), clock.instant());
        admin.setMustChangePassword(true);
        try {
            users.saveAndFlush(admin);
            audit.recordSystem(AuditAction.USER_CREATED, "USER", admin.getId(),
                    Map.of("email", admin.getEmail(), "role", Role.ADMIN.name(), "source", "ADMIN_BOOTSTRAP"));
            log.info("Created the first admin, {}; they must choose a new password at first sign-in. "
                    + "ADMIN_BOOTSTRAP_PASSWORD can now be removed", admin.getEmail());
        } catch (DataIntegrityViolationException e) {
            // Another instance starting at the same time created it first
            log.info("The first admin was created by another instance");
        }
    }

    private String fullName() {
        String name = properties.fullName();
        return name == null || name.isBlank() ? "Administrator" : name.strip();
    }
}
