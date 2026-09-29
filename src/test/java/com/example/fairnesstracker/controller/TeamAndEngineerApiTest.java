package com.example.fairnesstracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The full stack against H2: controllers, services, repositories
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TeamAndEngineerApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void teamLifecycleAndValidation() throws Exception {
        post("/api/teams", "{\"name\": \"SRE\", \"timezone\": \"Europe/London\"}", 201)
                .andExpect(jsonPath("$.timezone").value("Europe/London"));
        post("/api/teams", "{\"name\": \"SRE\"}", 409);
        post("/api/teams", "{\"name\": \"Ops\", \"timezone\": \"Mars/Olympus\"}", 400)
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unknown timezone")));
        post("/api/teams", "{\"name\": \"Ops\"}", 201)
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"));
    }

    @Test
    void engineerNeedsAnExistingTeam() throws Exception {
        post("/api/engineers", engineer("Asha", "asha@example.com", "Nope"), 400)
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unknown team")));

        post("/api/teams", "{\"name\": \"SRE\"}", 201);
        post("/api/engineers", engineer("Asha", "asha@example.com", "SRE"), 201)
                .andExpect(jsonPath("$.team").value("SRE"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void teamWithEngineersCannotBeDeleted() throws Exception {
        post("/api/teams", "{\"name\": \"SRE\"}", 201);
        post("/api/engineers", engineer("Asha", "asha@example.com", "SRE"), 201);

        mvc.perform(delete("/api/teams/1")).andExpect(status().isConflict());
    }

    @Test
    void engineerWithAlertsIsDeactivatedNotDeleted() throws Exception {
        post("/api/teams", "{\"name\": \"SRE\"}", 201);
        post("/api/engineers", engineer("Asha", "asha@example.com", "SRE"), 201);
        post("/api/alerts", "{\"engineerId\": 1, \"severity\": \"P2\"}", 201)
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("triggered"));

        mvc.perform(delete("/api/engineers/1")).andExpect(status().isConflict());
        mvc.perform(put("/api/engineers/1").contentType(MediaType.APPLICATION_JSON)
                        .content(engineer("Asha", "asha@example.com", "SRE").replace("}", ", \"active\": false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/alerts/1/assignments"))
                .andExpect(jsonPath("$[0].kind").value("PAGED"))
                .andExpect(jsonPath("$[0].source").value("MANUAL"));
    }

    private org.springframework.test.web.servlet.ResultActions post(String path, String body, int status)
            throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status));
    }

    private static String engineer(String name, String email, String team) {
        return "{\"name\": \"%s\", \"email\": \"%s\", \"team\": \"%s\"}".formatted(name, email, team);
    }
}
