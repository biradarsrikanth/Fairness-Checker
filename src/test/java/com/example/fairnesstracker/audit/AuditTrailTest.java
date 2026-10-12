package com.example.fairnesstracker.audit;

import com.example.fairnesstracker.auth.RefreshTokenRepository;
import com.example.fairnesstracker.security.JwtService;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-ins leave a trail an admin can read; nobody else can. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditTrailTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbc;

    private AppUser admin;

    @BeforeEach
    void setUp() {
        // H2 has no append-only trigger; start each test from an empty log
        jdbc.update("DELETE FROM audit_log");
        admin = new AppUser("admin@example.com", "Admin", Role.ADMIN);
        admin.setPassword(passwordEncoder.encode(PASSWORD), Instant.now());
        admin = users.save(admin);
    }

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void signInsAreRecordedWithWhoWhereAndWhy() throws Exception {
        login("admin@example.com", "wrong-password-1");
        login("ghost@example.com", PASSWORD);
        String signedInAs = JsonPath.read(login("admin@example.com", PASSWORD).andReturn().getResponse()
                .getContentAsString(), "$.user.email");

        audit(get("/api/v1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // Newest first
                .andExpect(jsonPath("$.content[0].action").value("LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.content[0].userId").value(admin.getId()))
                .andExpect(jsonPath("$.content[0].actorEmail").value(signedInAs))
                .andExpect(jsonPath("$.content[0].ip").value("198.51.100.7"))
                .andExpect(jsonPath("$.content[0].requestId").isNotEmpty())
                .andExpect(jsonPath("$.content[1].action").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.content[1].userId").isEmpty())
                .andExpect(jsonPath("$.content[1].actorEmail").value("ghost@example.com"))
                .andExpect(jsonPath("$.content[1].details.reason").value("UNKNOWN_EMAIL"))
                .andExpect(jsonPath("$.content[2].details.reason").value("WRONG_PASSWORD"))
                .andExpect(jsonPath("$.content[2].details.failuresInARow").value(1));

        audit(get("/api/v1/audit").param("action", "LOGIN_FAILED").param("userId", admin.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyAdminsReadTheAuditLog() throws Exception {
        AppUser manager = new AppUser("manager@example.com", "Manager", Role.MANAGER);
        manager.setPassword("{noop}x", Instant.now());
        users.save(manager);

        mvc.perform(get("/api/v1/audit").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(manager).value()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.7");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions audit(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(admin).value()));
    }
}
