package com.example.fairnesstracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The OpenAPI spec is what the frontend generates its API client from, so its shape is a contract
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void documentsEveryRouteGroupUnderV1() throws Exception {
        JsonNode spec = spec();
        List<String> paths = new ArrayList<>();
        spec.path("paths").fieldNames().forEachRemaining(paths::add);

        assertThat(paths).contains(
                "/api/v1/teams", "/api/v1/engineers/{id}", "/api/v1/alerts/{id}/assignments", "/api/v1/oncall",
                "/api/v1/scores/fairness", "/api/v1/scores/engineers/{engineerId}", "/api/v1/pagerduty/sync",
                "/api/webhook/pagerduty");
        // Only the webhook lives outside /api/v1; the dev controller and the root banner aren't documented
        assertThat(paths).allMatch(p -> p.startsWith("/api/v1/") || p.equals("/api/webhook/pagerduty"));
        assertThat(paths).noneMatch(p -> p.startsWith("/api/v1/dev"));
    }

    @Test
    void scoreWindowIsDocumentedAsQueryParameters() throws Exception {
        JsonNode parameters = spec().at("/paths/~1api~1v1~1scores~1fairness/get/parameters");
        List<String> query = new ArrayList<>();
        parameters.forEach(p -> {
            assertThat(p.path("in").asText()).isEqualTo("query");
            query.add(p.path("name").asText());
        });
        assertThat(query).containsExactlyInAnyOrder("days", "start", "end", "team");
    }

    @Test
    void groupsOperationsByTag() throws Exception {
        List<String> tags = new ArrayList<>();
        spec().path("tags").forEach(t -> tags.add(t.path("name").asText()));
        assertThat(tags).contains("Teams", "Engineers", "Alerts", "On-call", "Scores", "PagerDuty", "Webhooks");
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    private JsonNode spec() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Nested
    @SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class WhenDisabled {

        @Autowired
        private MockMvc mvc;

        @Test
        void hidesSpecAndUi() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        }
    }
}
