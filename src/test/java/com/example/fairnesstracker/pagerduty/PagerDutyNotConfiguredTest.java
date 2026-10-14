package com.example.fairnesstracker.pagerduty;

import com.example.fairnesstracker.pagerduty.sync.SyncRunRepository;
import com.example.fairnesstracker.pagerduty.sync.SyncRunner;
import com.example.fairnesstracker.security.JwtService;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A server without a PagerDuty API token starts, says so, and refuses syncs without recording failed runs. */
@SpringBootTest(properties = {"pagerduty.api-token=", "pagerduty.sync.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagerDutyNotConfiguredTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private AppUserRepository users;
    @Autowired private SyncRunRepository runs;
    @Autowired private SyncRunner syncRunner;

    private String admin;

    @BeforeEach
    void signIn() {
        AppUser user = new AppUser("admin-" + System.nanoTime() + "@example.com", "Test Admin", Role.ADMIN);
        user.setPassword("{noop}unused", Instant.now());
        admin = "Bearer " + jwtService.issue(users.save(user)).value();
    }

    @Test
    void statusSaysPagerDutyIsNotSetUpAndNotScheduled() throws Exception {
        mvc.perform(as(get("/api/v1/pagerduty/sync-status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.scheduled").value(false))
                .andExpect(jsonPath("$.intervalSeconds").isEmpty());
    }

    @Test
    void manualSyncsAreRefusedAndLeaveNoFailedRun() throws Exception {
        mvc.perform(as(post("/api/v1/pagerduty/sync")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAGERDUTY_NOT_CONFIGURED"));
        mvc.perform(as(post("/api/v1/pagerduty/sync/oncalls")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAGERDUTY_NOT_CONFIGURED"));
        mvc.perform(as(get("/api/v1/pagerduty/incidents")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAGERDUTY_NOT_CONFIGURED"));

        new PagerDutyScheduler(syncRunner).syncPagerDuty();

        assertThat(runs.count()).isZero();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, admin);
    }
}
