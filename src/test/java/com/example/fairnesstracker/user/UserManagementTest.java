package com.example.fairnesstracker.user;

import com.example.fairnesstracker.audit.AuditAction;
import com.example.fairnesstracker.audit.AuditLogRepository;
import com.example.fairnesstracker.auth.RefreshTokenRepository;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.security.JwtService;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/v1/users as an admin with a real token (so "your own account" rules apply). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AppUserRepository users;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private AuditLogRepository auditLog;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private AppUser admin;
    private String adminToken;
    private Team sre;
    private Engineer asha;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM audit_log");
        admin = new AppUser("admin@example.com", "Admin", Role.ADMIN);
        admin.setPassword(passwordEncoder.encode("admin-password-1"), Instant.now());
        admin = users.save(admin);
        adminToken = "Bearer " + jwtService.issue(admin).value();
        sre = teams.save(new Team("SRE", "Asia/Kolkata"));
        asha = new Engineer();
        asha.setName("Asha");
        asha.setEmail("asha@example.com");
        asha.setTeam(sre);
        asha = engineers.save(asha);
    }

    @AfterEach
    void cleanUp() {
        refreshTokens.deleteAll();
        users.deleteAll();
        engineers.deleteAll();
        teams.deleteAll();
    }

    // --- Creating ------------------------------------------------------------------------------------

    @Test
    void aNewUserGetsAOneTimePasswordTheyMustChange() throws Exception {
        MvcResult created = call(json(post("/api/v1/users"), "{\"email\": \"Mira@Example.com\", \"fullName\": \"Mira\", "
                + "\"role\": \"MANAGER\", \"teamIds\": [" + sre.getId() + "]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.user.role").value("MANAGER"))
                .andExpect(jsonPath("$.user.teams[0].name").value("SRE"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        String password = JsonPath.read(created.getResponse().getContentAsString(), "$.temporaryPassword");
        assertThat(password).hasSize(16);

        login("mira@example.com", password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));
        assertThat(auditLog.findAll()).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo(AuditAction.USER_CREATED);
            assertThat(entry.getUserId()).isEqualTo(admin.getId());
            assertThat(entry.getDetails()).containsEntry("role", "MANAGER");
        });
    }

    @Test
    void creationRules() throws Exception {
        String engineerLogin = "{\"email\": \"asha.login@example.com\", \"fullName\": \"Asha\", \"role\": \"ENGINEER\"%s}";

        fieldError(call(json(post("/api/v1/users"), engineerLogin.formatted(""))), 400, "engineerId");
        call(json(post("/api/v1/users"), engineerLogin.formatted(", \"engineerId\": " + asha.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.engineerName").value("Asha"));
        // One login per engineer, one user per email (whatever the case), teams only for managers
        fieldError(call(json(post("/api/v1/users"), "{\"email\": \"other@example.com\", \"fullName\": \"X\", "
                + "\"role\": \"ENGINEER\", \"engineerId\": " + asha.getId() + "}")), 409, "engineerId");
        fieldError(call(json(post("/api/v1/users"), "{\"email\": \"ADMIN@example.com\", \"fullName\": \"X\", "
                + "\"role\": \"ADMIN\"}")), 409, "email");
        fieldError(call(json(post("/api/v1/users"), "{\"email\": \"x@example.com\", \"fullName\": \"X\", "
                + "\"role\": \"ADMIN\", \"teamIds\": [" + sre.getId() + "]}")), 400, "teamIds");
        fieldError(call(json(post("/api/v1/users"), "{\"email\": \"x@example.com\", \"fullName\": \"X\", "
                + "\"role\": \"MANAGER\", \"teamIds\": [999999]}")), 400, "teamIds");
    }

    // --- Changing ------------------------------------------------------------------------------------

    @Test
    void leavingTheManagerRoleDropsTeamsAndEndsSessions() throws Exception {
        long id = createManager("mira@example.com");
        String password = resetPassword(id);
        login("mira@example.com", password).andExpect(status().isOk());

        call(json(put("/api/v1/users/{id}", id), "{\"email\": \"mira@example.com\", \"fullName\": \"Mira\", "
                + "\"role\": \"ENGINEER\", \"engineerId\": " + asha.getId() + ", \"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ENGINEER"))
                .andExpect(jsonPath("$.teams").isEmpty());

        assertThat(refreshTokens.findAll()).allMatch(token -> token.getRevokedAt() != null);
    }

    @Test
    void anAdminCantLockThemselvesOut() throws Exception {
        String self = "{\"email\": \"admin@example.com\", \"fullName\": \"Admin\", \"role\": \"%s\", \"enabled\": %s}";
        call(json(put("/api/v1/users/{id}", admin.getId()), self.formatted("MANAGER", "true"))).andExpect(status().isConflict());
        call(json(put("/api/v1/users/{id}", admin.getId()), self.formatted("ADMIN", "false"))).andExpect(status().isConflict());
        call(delete("/api/v1/users/{id}", admin.getId())).andExpect(status().isConflict());
        // Renaming yourself is fine
        call(json(put("/api/v1/users/{id}", admin.getId()), self.formatted("ADMIN", "true").replace("\"Admin\"", "\"Head Admin\"")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theLastEnabledAdminStays() throws Exception {
        // A mock admin isn't a user, so admin@example.com is the only admin there is
        mvc.perform(json(put("/api/v1/users/{id}", admin.getId()), "{\"email\": \"admin@example.com\", "
                        + "\"fullName\": \"Admin\", \"role\": \"ADMIN\", \"enabled\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This is the last enabled admin; make someone else an admin first"));
        mvc.perform(delete("/api/v1/users/{id}", admin.getId())).andExpect(status().isConflict());
    }

    @Test
    void teamAccessIsForManagersOnly() throws Exception {
        long id = createManager("mira@example.com");
        Team platform = teams.save(new Team("Platform", "Asia/Kolkata"));

        call(json(put("/api/v1/users/{id}/teams", id), "{\"teamIds\": [" + sre.getId() + ", " + platform.getId() + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teams[0].name").value("Platform"))
                .andExpect(jsonPath("$.teams[1].name").value("SRE"));
        fieldError(call(json(put("/api/v1/users/{id}/teams", admin.getId()), "{\"teamIds\": [" + sre.getId() + "]}")),
                400, "teamIds");
    }

    @Test
    void aResetPasswordReplacesTheOldOneAndLiftsTheLock() throws Exception {
        long id = createManager("mira@example.com");
        String first = resetPassword(id);
        AppUser mira = users.findById(id).orElseThrow();
        mira.setLockedUntil(Instant.now().plusSeconds(600));
        users.save(mira);

        String second = resetPassword(id);

        login("mira@example.com", first).andExpect(status().isUnauthorized());
        login("mira@example.com", second).andExpect(status().isOk());
    }

    @Test
    void unlockingAndDeleting() throws Exception {
        long id = createManager("mira@example.com");
        AppUser mira = users.findById(id).orElseThrow();
        mira.setFailedLoginCount(3);
        mira.setLockedUntil(Instant.now().plusSeconds(600));
        users.save(mira);

        call(post("/api/v1/users/{id}/unlock", id)).andExpect(status().isOk()).andExpect(jsonPath("$.lockedUntil").isEmpty());

        call(delete("/api/v1/users/{id}", id)).andExpect(status().isNoContent());
        call(get("/api/v1/users/{id}", id)).andExpect(status().isNotFound());
        assertThat(auditLog.findAll()).extracting(entry -> entry.getAction())
                .contains(AuditAction.USER_UNLOCKED, AuditAction.USER_DELETED);
    }

    // --- Reading -------------------------------------------------------------------------------------

    @Test
    void listingFiltersAndSorts() throws Exception {
        createManager("zed@example.com");
        createManager("mira@example.com");

        call(get("/api/v1/users").param("role", "MANAGER"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].email").value("mira@example.com"));
        call(get("/api/v1/users").param("q", "ZED")).andExpect(jsonPath("$.totalElements").value(1));
        call(get("/api/v1/users").param("sort", "passwordHash")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsManageUsers() throws Exception {
        long id = createManager("mira@example.com");
        String manager = "Bearer " + jwtService.issue(users.findById(id).orElseThrow()).value();

        mvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------------------------------

    private long createManager(String email) throws Exception {
        MvcResult result = call(json(post("/api/v1/users"), "{\"email\": \"" + email + "\", \"fullName\": \"Mira\", "
                + "\"role\": \"MANAGER\", \"teamIds\": [" + sre.getId() + "]}")).andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.user.id")).longValue();
    }

    private String resetPassword(long id) throws Exception {
        MvcResult result = call(post("/api/v1/users/{id}/reset-password", id)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.temporaryPassword");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(json(post("/api/v1/auth/login"),
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private static void fieldError(ResultActions result, int status, String field) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.errors[0].field").value(field));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
