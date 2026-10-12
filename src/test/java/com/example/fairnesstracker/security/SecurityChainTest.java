package com.example.fairnesstracker.security;

import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The filter chain with real access tokens: what's public, what needs a token, and the error bodies. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityChainTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;

    @Test
    void protectedRoutesNeedAToken() throws Exception {
        mvc.perform(get("/api/v1/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/teams"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void aBadTokenIsRejectedWithTheSameErrorShape() throws Exception {
        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("Access token is invalid or expired; refresh it or sign in again"));
    }

    @Test
    void aValidTokenGetsIn() throws Exception {
        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(user(Role.ADMIN, false))))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoutesNeedNoToken() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        // The webhook checks PagerDuty's signature itself: rejected by the controller, not by the chain
        mvc.perform(post("/api/webhook/pagerduty").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void aUserWhoMustChangeTheirPasswordCanDoNothingElse() throws Exception {
        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(user(Role.ADMIN, true))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    private String bearer(AppUser user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private static AppUser user(Role role, boolean mustChangePassword) {
        AppUser user = new AppUser("someone@example.com", "Someone", role);
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setMustChangePassword(mustChangePassword);
        return user;
    }
}
