package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Account lockout and the per-IP sign-in limit, with the default settings (5 failures, 15 minutes, 10/min). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginProtectionTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUser() {
        AppUser admin = new AppUser("admin@example.com", "Admin", Role.ADMIN);
        admin.setPassword(passwordEncoder.encode(PASSWORD), Instant.now());
        users.save(admin);
    }

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() throws Exception {
        for (int i = 1; i <= 4; i++) {
            login(PASSWORD + "-wrong", "198.51.100.1").andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login(PASSWORD + "-wrong", "198.51.100.1")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.detail").value("Too many failed sign-ins; try again in 15 minutes"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"));

        // Even the right password, from anywhere, until the lock ends
        login(PASSWORD, "203.0.113.9")
                .andExpect(status().isLocked())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void theLockEndsAndASuccessfulSignInResetsTheCount() throws Exception {
        AppUser admin = users.findByEmail("admin@example.com").orElseThrow();
        admin.setFailedLoginCount(4);
        admin.setLockedUntil(Instant.now().minus(Duration.ofSeconds(1)));
        users.save(admin);

        login(PASSWORD, "198.51.100.2").andExpect(status().isOk());

        AppUser after = users.findByEmail("admin@example.com").orElseThrow();
        assertThat(after.getFailedLoginCount()).isZero();
        assertThat(after.getLockedUntil()).isNull();
    }

    @Test
    void anIpGetsTenAttemptsAMinute() throws Exception {
        // Different, unknown accounts: the limit is per client, whoever they try to be
        for (int i = 1; i <= 10; i++) {
            login("nobody" + i + "@example.com", "192.0.2.50", PASSWORD).andExpect(status().isUnauthorized());
        }
        login("admin@example.com", "192.0.2.50", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // Another client isn't affected
        login(PASSWORD, "192.0.2.51").andExpect(status().isOk());
    }

    private ResultActions login(String password, String ip) throws Exception {
        return login("admin@example.com", ip, password);
    }

    private ResultActions login(String email, String ip, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }
}
