package com.example.fairnesstracker.security;

import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private AppUserRepository users;

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
    void anExpiredTokenIsRejected() throws Exception {
        // Signed with our key and otherwise valid, but it expired a minute (plus the allowed skew) ago
        Instant past = Instant.now().minus(Duration.ofMinutes(20));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("fairness-checker").audience(List.of("fairness-web"))
                .subject("7").issuedAt(past).expiresAt(Instant.now().minus(Duration.ofSeconds(90)))
                .claim(JwtService.ROLE_CLAIM, "ADMIN").build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();

        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""));
    }

    @Test
    void aValidTokenGetsIn() throws Exception {
        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(user(Role.ADMIN, false))))
                .andExpect(status().isOk());
    }

    @Test
    void aTokenStopsWorkingWhenItsAccountIsDisabled() throws Exception {
        AppUser user = user(Role.ADMIN, false);
        String token = bearer(user);
        user.setEnabled(false);
        users.save(user);

        // /auth/me reads the account itself but not the caller's scope: the chain must turn it away first
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value("This account has been disabled"));
    }

    @Test
    void aTokenStopsWorkingWhenItsAccountIsDeleted() throws Exception {
        AppUser user = user(Role.MANAGER, false);
        String token = bearer(user);
        users.delete(user);

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aDisabledAccountIsTurnedAwayBeforeAskingForANewPassword() throws Exception {
        AppUser user = user(Role.ADMIN, true);
        String token = bearer(user);
        user.setEnabled(false);
        users.save(user);

        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("This account has been disabled"));
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

    // Saved, as tokens of accounts that don't exist (any more) are turned away
    private AppUser user(Role role, boolean mustChangePassword) {
        AppUser user = new AppUser("someone-" + UUID.randomUUID() + "@example.com", "Someone", role);
        user.setPassword("{noop}unused", Instant.now());
        user.setMustChangePassword(mustChangePassword);
        return users.save(user);
    }
}
