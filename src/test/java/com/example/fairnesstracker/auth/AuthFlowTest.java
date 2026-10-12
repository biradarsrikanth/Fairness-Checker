package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in to sign-out through the real endpoints, tokens and cookie. No test transaction: each call commits. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    private static final String COOKIE = "fairness_refresh";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
        engineers.deleteAll();
        teams.deleteAll();
    }

    // --- Signing in ----------------------------------------------------------------------------------

    @Test
    void signingInReturnsAnAccessTokenAndSetsTheRefreshCookie() throws Exception {
        AppUser manager = user("Manager@Example.com", Role.MANAGER, false);
        Team sre = teams.save(new Team("SRE", "Asia/Kolkata"));
        manager.getTeams().add(sre);
        users.save(manager);

        MvcResult result = login("manager@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString(COOKIE + "="),
                        containsString("Path=/api/v1/auth"), containsString("HttpOnly"), containsString("Secure"),
                        containsString("SameSite=Strict"), containsString("Max-Age=6047") /* 7 days, give or take the test's own run time */)))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("Manager@Example.com"))
                .andExpect(jsonPath("$.user.role").value("MANAGER"))
                .andExpect(jsonPath("$.user.teams[0].name").value("SRE"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();

        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(result)))
                .andExpect(status().isOk());
        assertThat(users.findByEmail("manager@example.com").orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailLookTheSame() throws Exception {
        users.save(user("admin@example.com", Role.ADMIN, false));

        login("admin@example.com", "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Wrong email or password"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Wrong email or password"));
    }

    @Test
    void aDisabledAccountCantSignIn() throws Exception {
        AppUser admin = user("admin@example.com", Role.ADMIN, false);
        admin.setEnabled(false);
        users.save(admin);

        login("admin@example.com", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void signInNeedsAnEmailAndAPassword() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    // --- Refreshing and signing out ------------------------------------------------------------------

    @Test
    void refreshingReplacesTheCookieAndReturnsANewAccessToken() throws Exception {
        users.save(user("admin@example.com", Role.ADMIN, false));
        Cookie first = refreshCookie(login("admin@example.com", PASSWORD).andReturn());

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("admin@example.com"))
                .andReturn();

        assertThat(refreshCookie(refreshed).getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(refreshed)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshingWithoutAValidCookieFailsAndClearsIt() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, "forged")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        users.save(user("admin@example.com", Role.ADMIN, false));
        Cookie cookie = refreshCookie(login("admin@example.com", PASSWORD).andReturn());

        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        // Signing out twice, or without a cookie, is harmless
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    // --- Who am I, and changing the password ---------------------------------------------------------

    @Test
    void meDescribesAnEngineerLogin() throws Exception {
        Team sre = teams.save(new Team("SRE", "Asia/Kolkata"));
        Engineer asha = new Engineer();
        asha.setName("Asha");
        asha.setEmail("asha@example.com");
        asha.setTeam(sre);
        engineers.save(asha);
        AppUser login = user("asha@example.com", Role.ENGINEER, false);
        login.setEngineer(asha);
        users.save(login);

        String token = bearer(login("asha@example.com", PASSWORD).andReturn());

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ENGINEER"))
                .andExpect(jsonPath("$.engineerId").value(asha.getId()))
                .andExpect(jsonPath("$.teams[0].name").value("SRE"))
                .andExpect(jsonPath("$.teams.length()").value(1));
    }

    @Test
    void theFirstAdminMustChangeTheBootstrapPasswordBeforeAnythingElse() throws Exception {
        users.save(user("admin@example.com", Role.ADMIN, true));
        MvcResult signedIn = login("admin@example.com", PASSWORD)
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        String token = bearer(signedIn);
        Cookie oldSession = refreshCookie(signedIn);

        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());

        changePassword(token, "wrong-current-pw", "a-brand-new-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        changePassword(token, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        changePassword(token, PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

        MvcResult changed = changePassword(token, PASSWORD, "a-brand-new-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();

        mvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(changed)))
                .andExpect(status().isOk());
        // Sessions from before the change are over; the new password works, the old one doesn't
        mvc.perform(post("/api/v1/auth/refresh").cookie(oldSession)).andExpect(status().isUnauthorized());
        login("admin@example.com", PASSWORD).andExpect(status().isUnauthorized());
        login("admin@example.com", "a-brand-new-password").andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------------------------------

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/v1/auth/change-password").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next)));
    }

    private static String bearer(MvcResult result) throws Exception {
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static Cookie refreshCookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String value = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        return new Cookie(COOKIE, value);
    }

    private AppUser user(String email, Role role, boolean mustChangePassword) {
        AppUser user = new AppUser(email, "Test User", role);
        user.setPassword(passwordEncoder.encode(PASSWORD), Instant.now());
        user.setMustChangePassword(mustChangePassword);
        return user;
    }
}
