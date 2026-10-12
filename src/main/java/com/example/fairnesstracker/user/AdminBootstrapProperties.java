package com.example.fairnesstracker.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The first admin. Both email and password, or neither; checked at startup. */
@Validated
@ConfigurationProperties("auth.bootstrap-admin")
public record AdminBootstrapProperties(@Email String email, String password, String fullName) {

    static final int MIN_PASSWORD_LENGTH = 12;

    public boolean isConfigured() {
        return hasText(email) && hasText(password);
    }

    @AssertTrue(message = "set both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD, or neither")
    public boolean isComplete() {
        return hasText(email) == hasText(password);
    }

    @AssertTrue(message = "must be at least " + MIN_PASSWORD_LENGTH + " characters")
    public boolean isPasswordLongEnough() {
        return !hasText(password) || password.length() >= MIN_PASSWORD_LENGTH;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
