package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditLogRepository;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A stolen refresh cookie used after its owner refreshed: both are signed out. No grace period here. */
@SpringBootTest(properties = "auth.refresh-token.reuse-grace=0s")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenReuseApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private AuditLogRepository auditLog;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void reusingARotatedCookieEndsTheWholeSession() throws Exception {
        AppUser user = new AppUser("asha@example.com", "Asha", Role.ADMIN);
        user.setPassword(passwordEncoder.encode("correct-horse-battery"), Instant.now());
        users.save(user);
        Cookie stolen = cookie(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"asha@example.com\", \"password\": \"correct-horse-battery\"}")).andReturn());
        Cookie current = cookie(mvc.perform(post("/api/v1/auth/refresh").cookie(stolen))
                .andExpect(status().isOk()).andReturn());
        Thread.sleep(5);

        mvc.perform(post("/api/v1/auth/refresh").cookie(stolen))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(current))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        assertThat(auditLog.findAll()).anyMatch(entry -> entry.getAction() == AuditAction.REFRESH_TOKEN_REUSED
                && user.getId().equals(entry.getUserId()));
    }

    private static Cookie cookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return new Cookie("fairness_refresh", header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }
}
