package com.example.fairnesstracker.security;

import com.example.fairnesstracker.alert.AlertAssignmentRepository;
import com.example.fairnesstracker.alert.AlertEvent;
import com.example.fairnesstracker.alert.AlertRepository;
import com.example.fairnesstracker.auth.RefreshTokenRepository;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who sees and changes what, with real tokens. Teams SRE (Asha, Ravi) and Platform (Priya), one alert each;
 * an admin, a manager of SRE, and Asha's engineer login. Score calls that pass the access checks reach the
 * scorer, which isn't running in tests (503): that 503 shows the request was let through.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessRulesTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AppUserRepository users;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;
    @Autowired private AlertRepository alerts;
    @Autowired private AlertAssignmentRepository assignments;
    @Autowired private RefreshTokenRepository refreshTokens;

    private Team sre;
    private Team platform;
    private Engineer asha;
    private Engineer ravi;
    private Engineer priya;
    private AlertEvent raviAlert;
    private AlertEvent priyaAlert;
    private String admin;
    private String manager;
    private String engineer;

    @BeforeEach
    void seed() {
        sre = teams.save(new Team("SRE", "Asia/Kolkata"));
        platform = teams.save(new Team("Platform", "Asia/Kolkata"));
        asha = engineer("Asha", sre);
        ravi = engineer("Ravi", sre);
        priya = engineer("Priya", platform);
        alert(asha);
        raviAlert = alert(ravi);
        priyaAlert = alert(priya);

        admin = token(user("admin@example.com", Role.ADMIN));
        AppUser sreManager = user("manager@example.com", Role.MANAGER);
        sreManager.getTeams().add(sre);
        manager = token(users.save(sreManager));
        AppUser ashaLogin = user("asha.login@example.com", Role.ENGINEER);
        ashaLogin.setEngineer(asha);
        engineer = token(users.save(ashaLogin));
    }

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
        assignments.deleteAll();
        alerts.deleteAll();
        engineers.deleteAll();
        teams.deleteAll();
    }

    // --- Lists are limited, not refused --------------------------------------------------------------

    @Test
    void eachRoleSeesItsOwnSliceOfTheAlerts() throws Exception {
        call(admin, get("/api/v1/alerts")).andExpect(jsonPath("$.totalElements").value(3));
        call(manager, get("/api/v1/alerts")).andExpect(jsonPath("$.totalElements").value(2));
        call(engineer, get("/api/v1/alerts"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].engineerId").value(asha.getId()));
        // A filter can narrow the slice, never widen it
        call(manager, get("/api/v1/alerts").param("team", "Platform")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void engineersAndShiftsAreLimitedTheSameWay() throws Exception {
        call(manager, get("/api/v1/engineers")).andExpect(jsonPath("$.totalElements").value(2));
        call(engineer, get("/api/v1/engineers"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Asha"));
        call(engineer, get("/api/v1/oncall").param("start", "2026-09-01").param("end", "2026-09-30")
                .param("engineerId", ravi.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- Single records ------------------------------------------------------------------------------

    @Test
    void someoneElsesRecordIsForbidden() throws Exception {
        forbidden(call(engineer, get("/api/v1/alerts/{id}", raviAlert.getId())));
        forbidden(call(engineer, get("/api/v1/alerts/{id}/assignments", raviAlert.getId())));
        forbidden(call(engineer, get("/api/v1/engineers/{id}", ravi.getId())));
        forbidden(call(manager, get("/api/v1/alerts/{id}", priyaAlert.getId())));
        forbidden(call(manager, get("/api/v1/engineers/{id}", priya.getId())));

        call(manager, get("/api/v1/alerts/{id}", raviAlert.getId())).andExpect(status().isOk());
        call(engineer, get("/api/v1/engineers/{id}", asha.getId())).andExpect(status().isOk());
        // Missing records are still 404s, not 403s
        call(manager, get("/api/v1/engineers/999999")).andExpect(status().isNotFound());
    }

    // --- Changes -------------------------------------------------------------------------------------

    @Test
    void managersChangeOnlyTheirOwnTeams() throws Exception {
        call(manager, json(post("/api/v1/engineers"), engineerJson("Neha", "neha@example.com", "SRE")))
                .andExpect(status().isCreated());
        forbidden(call(manager, json(post("/api/v1/engineers"), engineerJson("Omar", "omar@example.com", "Platform"))));
        // Moving an engineer out to a team they don't manage, or editing someone else's
        forbidden(call(manager, json(put("/api/v1/engineers/{id}", ravi.getId()),
                engineerJson("Ravi", "ravi@example.com", "Platform"))));
        forbidden(call(manager, json(put("/api/v1/engineers/{id}", priya.getId()),
                engineerJson("Priya", "priya@example.com", "Platform"))));

        call(manager, json(put("/api/v1/teams/{id}", sre.getId()), "{\"name\": \"SRE\", \"timezone\": \"UTC\"}"))
                .andExpect(status().isOk());
        forbidden(call(manager, json(put("/api/v1/teams/{id}", platform.getId()), "{\"name\": \"Platform\"}")));
    }

    @Test
    void someChangesAreForAdminsOnly() throws Exception {
        forbidden(call(manager, json(post("/api/v1/teams"), "{\"name\": \"Ops\"}")));
        forbidden(call(manager, delete("/api/v1/teams/{id}", platform.getId())));
        forbidden(call(manager, delete("/api/v1/alerts/{id}", raviAlert.getId())));
        forbidden(call(manager, post("/api/v1/pagerduty/sync")));

        call(admin, json(post("/api/v1/teams"), "{\"name\": \"Ops\"}")).andExpect(status().isCreated());
        call(admin, delete("/api/v1/alerts/{id}", raviAlert.getId())).andExpect(status().isNoContent());
    }

    @Test
    void engineersChangeNothing() throws Exception {
        forbidden(call(engineer, json(post("/api/v1/engineers"), engineerJson("Neha", "neha@example.com", "SRE"))));
        forbidden(call(engineer, json(post("/api/v1/alerts"), "{\"engineerId\": " + asha.getId() + ", \"severity\": \"P2\"}")));
        forbidden(call(engineer, json(put("/api/v1/teams/{id}", sre.getId()), "{\"name\": \"SRE\"}")));
        forbidden(call(engineer, get("/api/v1/pagerduty/users")));
    }

    // --- Scores --------------------------------------------------------------------------------------

    @Test
    void peersBurnoutIsPrivate() throws Exception {
        forbidden(call(engineer, get("/api/v1/scores/burnout")));
        forbidden(call(engineer, get("/api/v1/scores/engineers/{id}", ravi.getId())));
        forbidden(call(manager, get("/api/v1/scores/burnout").param("team", "Platform")));
        forbidden(call(manager, get("/api/v1/scores/engineers/{id}", priya.getId())));

        reachedTheScorer(call(engineer, get("/api/v1/scores/engineers/{id}", asha.getId())));
        reachedTheScorer(call(manager, get("/api/v1/scores/burnout")));
        reachedTheScorer(call(manager, get("/api/v1/scores/engineers/{id}", ravi.getId())));
    }

    @Test
    void teamScoresDefaultToTheOnlyTeamYouCanSee() throws Exception {
        reachedTheScorer(call(engineer, get("/api/v1/scores/fairness")));
        reachedTheScorer(call(engineer, get("/api/v1/scores/timeofday").param("team", "SRE")));
        forbidden(call(engineer, get("/api/v1/scores/fairness").param("team", "Platform")));
        forbidden(call(manager, get("/api/v1/scores/timeofday").param("team", "Platform")));
        call(manager, get("/api/v1/scores/fairness").param("team", "Nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("team"));
    }

    @Test
    void aManagerOfSeveralTeamsMustSayWhich() throws Exception {
        AppUser both = user("both@example.com", Role.MANAGER);
        both.getTeams().add(sre);
        both.getTeams().add(platform);
        String token = token(users.save(both));

        call(token, get("/api/v1/scores/fairness"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("team"));
        reachedTheScorer(call(token, get("/api/v1/scores/fairness").param("team", "Platform")));
    }

    @Test
    void accessFollowsTheDatabaseNotTheToken() throws Exception {
        // Taking SRE away applies to the token already issued
        AppUser sreManager = users.findWithAccessById(users.findByEmail("manager@example.com").orElseThrow().getId())
                .orElseThrow();
        sreManager.getTeams().clear();
        users.save(sreManager);

        call(manager, get("/api/v1/alerts")).andExpect(jsonPath("$.totalElements").value(0));
        forbidden(call(manager, get("/api/v1/alerts/{id}", raviAlert.getId())));
    }

    // -------------------------------------------------------------------------------------------------

    private ResultActions call(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token));
    }

    private static void forbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static void reachedTheScorer(ResultActions result) throws Exception {
        result.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String engineerJson(String name, String email, String team) {
        return "{\"name\": \"%s\", \"email\": \"%s\", \"team\": \"%s\"}".formatted(name, email, team);
    }

    private String token(AppUser user) {
        return "Bearer " + jwtService.issue(user).value();
    }

    private AppUser user(String email, Role role) {
        AppUser user = new AppUser(email, "Test User", role);
        user.setPassword("{noop}unused", Instant.now());
        return role == Role.ADMIN ? users.save(user) : user;
    }

    private Engineer engineer(String name, Team team) {
        Engineer engineer = new Engineer();
        engineer.setName(name);
        engineer.setEmail(name.toLowerCase() + "@example.com");
        engineer.setTeam(team);
        return engineers.save(engineer);
    }

    private AlertEvent alert(Engineer engineer) {
        AlertEvent alert = new AlertEvent();
        alert.setEngineer(engineer);
        alert.setSeverity("P2");
        alert.setStatus(AlertEvent.STATUS_RESOLVED);
        alert.setTriggeredAt(LocalDateTime.of(2026, 9, 10, 10, 0));
        return alerts.save(alert);
    }
}
