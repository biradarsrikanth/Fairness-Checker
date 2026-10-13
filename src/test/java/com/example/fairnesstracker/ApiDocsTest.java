package com.example.fairnesstracker;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        paths.addAll(spec.path("paths").propertyNames());

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
            assertThat(p.path("in").asString()).isEqualTo("query");
            query.add(p.path("name").asString());
        });
        assertThat(query).containsExactlyInAnyOrder("days", "start", "end", "team");
    }

    @Test
    void groupsOperationsByTag() throws Exception {
        List<String> tags = new ArrayList<>();
        spec().path("tags").forEach(t -> tags.add(t.path("name").asString()));
        assertThat(tags).contains("Teams", "Engineers", "Alerts", "On-call", "Scores", "PagerDuty", "Webhooks");
    }

    @Test
    void documentsTheErrorModelOnEveryOperation() throws Exception {
        JsonNode spec = spec();
        assertThat(spec.at("/components/schemas/ApiError/properties").propertyNames()).containsExactlyInAnyOrder(
                "type", "title", "status", "detail", "instance", "code", "errors", "requestId", "timestamp");
        assertThat(spec.at("/paths/~1api~1v1~1teams/get/responses/default/content/application~1problem+json/schema/$ref")
                .asString()).isEqualTo("#/components/schemas/ApiError");
    }

    @Test
    void documentsBearerAuthExceptOnThePublicOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.refreshCookie.in").value("cookie"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/webhook/pagerduty'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security[0].refreshCookie").exists())
                // Everything else inherits the Bearer requirement
                .andExpect(jsonPath("$.paths['/api/v1/teams'].get.security").doesNotExist());
    }

    @Test
    void recordFieldsAreRequiredUnlessNullable() throws Exception {
        JsonNode schemas = spec().path("components").path("schemas");

        JsonNode user = schemas.path("UserResponse");
        assertThat(names(user.path("required")))
                .containsExactlyInAnyOrder("id", "email", "fullName", "role", "teams", "mustChangePassword");
        assertThat(names(user.at("/properties/engineerId/type"))).containsExactly("integer", "null");
        assertThat(user.at("/properties/email/type").asString()).isEqualTo("string");
        // A generic record, named after its type argument
        assertThat(names(schemas.at("/PageResponseAlertResponse/required")))
                .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");

        // A nullable reference: the referenced schema, or null
        JsonNode lastRun = schemas.at("/KindStatus/properties/lastRun");
        assertThat(lastRun.at("/oneOf/0/$ref").asString()).isEqualTo("#/components/schemas/SyncRunResponse");
        assertThat(lastRun.at("/oneOf/1/type").asString()).isEqualTo("null");
        assertThat(lastRun.path("description").asString()).isEqualTo("Null before the first sync");

        // Request bodies: a nullable component may be left out
        assertThat(names(schemas.at("/CreateUserRequest/required")))
                .containsExactlyInAnyOrder("email", "fullName", "role");
    }

    // They name the generated client's functions; springdoc's defaults (method names, "_1" on a clash) would
    // rename them whenever a controller method is added or renamed
    @Test
    void everyOperationHasItsOwnStableId() throws Exception {
        List<String> ids = new ArrayList<>();
        spec().path("paths").forEach(path -> path.forEach(operation ->
                ids.add(operation.path("operationId").asString())));

        assertThat(ids).doesNotHaveDuplicates().allMatch(id -> id.matches("[a-z][A-Za-z]+"));
        assertThat(ids).contains("listTeams", "getAnalyticsSummary", "refreshSession", "listSyncRuns",
                "receivePagerDutyWebhook");
    }

    // springdoc names a schema after the class's simple name, so of two classes with one name the second
    // silently takes the first one's place (the client then gets the wrong shape). Each needs @Schema(name).
    @Test
    void noTwoClassesShareASchemaName() throws Exception {
        Set<String> schemas = new HashSet<>(spec().path("components").path("schemas").propertyNames());
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((reader, factory) -> true);
        Map<String, List<String>> classesBySchema = new TreeMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.example.fairnesstracker")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            boolean testClass = type.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes");
            // Enums are inlined and entities never returned; OncallsResponse.Oncall is PagerDuty's, only read
            if (type.isEnum() || testClass || type.isAnnotationPresent(Entity.class)
                    || type.getName().endsWith("OncallsResponse$Oncall")) {
                continue;
            }
            Schema schema = type.getAnnotation(Schema.class);
            String name = schema != null && !schema.name().isEmpty() ? schema.name() : type.getSimpleName();
            if (schemas.contains(name)) {
                classesBySchema.computeIfAbsent(name, key -> new ArrayList<>()).add(type.getName());
            }
        }
        assertThat(classesBySchema).isNotEmpty()
                .allSatisfy((name, classes) -> assertThat(classes).as(name).hasSize(1));
        assertThat(spec().at("/components/schemas/AlertAssignment/properties").propertyNames())
                .contains("kind", "engineerName", "assignedAt");
        assertThat(spec().at("/components/schemas/ScoreWindow/properties/start/format").asString())
                .isEqualTo("date-time");
    }

    @Test
    void responsesAreJsonAndErrorsProblemDetails() throws Exception {
        JsonNode responses = spec().at("/paths/~1api~1v1~1auth~1login/post/responses");
        assertThat(responses.at("/200/content").propertyNames()).containsExactly("application/json");
        assertThat(responses.at("/default/content").propertyNames()).containsExactly("application/problem+json");
    }

    // fairness-web generates its client from this file (GATEWAY_SPEC=../Fairness-Checker/target/openapi.json
    // npm run api), so no gateway has to run for it
    @Test
    void savesTheSpecForTheWebClient() throws Exception {
        Path file = Path.of("target", "openapi.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec()));
        assertThat(file).isNotEmptyFile();
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(item -> names.add(item.asString()));
        return names;
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
