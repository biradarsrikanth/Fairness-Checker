package com.example.fairnesstracker.common.exception;

import org.springframework.security.test.context.support.WithMockUser;
import com.example.fairnesstracker.common.web.RequestIdFilter;
import com.example.fairnesstracker.engineer.Engineer;
import com.example.fairnesstracker.engineer.EngineerRepository;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every kind of failure answers with an {@link ApiError}: the right status, a stable code, a detail that
 * explains the problem without leaking internals, and field errors a form can show next to its inputs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
// Endpoint behaviour, signed in as an admin; access rules are covered by the security tests
@WithMockUser(roles = "ADMIN")
class ErrorContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private TeamRepository teams;
    @Autowired private EngineerRepository engineers;

    // --- Routing and protocol ------------------------------------------------------------------------

    @Test
    void unknownRoute() throws Exception {
        expect(get("/api/v1/nope"), 404, "NOT_FOUND")
                .andExpect(jsonPath("$.detail").value("No endpoint GET /api/v1/nope"))
                .andExpect(jsonPath("$.instance").value("/api/v1/nope"));
    }

    @Test
    void wrongMethodKeepsTheAllowHeader() throws Exception {
        expect(delete("/api/v1/teams"), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void unsupportedContentType() throws Exception {
        expect(post("/api/v1/teams").contentType(MediaType.TEXT_PLAIN).content("SRE"), 415,
                "UNSUPPORTED_MEDIA_TYPE");
    }

    // --- Request bodies ------------------------------------------------------------------------------

    @Test
    void brokenJsonIsMalformedNotAServerError() throws Exception {
        expect(json(post("/api/v1/teams"), "{\"name\": "), 400, "MALFORMED_REQUEST")
                .andExpect(jsonPath("$.detail").value("Request body is missing or isn't valid JSON"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void missingBodyIsMalformed() throws Exception {
        expect(json(post("/api/v1/teams"), ""), 400, "MALFORMED_REQUEST");
    }

    @Test
    void valueOfTheWrongTypeNamesItsField() throws Exception {
        expect(json(post("/api/v1/alerts"), "{\"engineerId\": \"abc\", \"severity\": \"P1\"}"), 400,
                "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("engineerId"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
    }

    @Test
    void beanValidationListsEveryFieldInOrder() throws Exception {
        expect(json(post("/api/v1/engineers"), "{\"name\": \"\", \"email\": \"not-an-email\", \"team\": \"\"}"),
                400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.detail").value(
                        "email: must be a well-formed email address; name: must not be blank; team: must not be blank"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("name"))
                .andExpect(jsonPath("$.errors[2].field").value("team"));
    }

    // --- Query and path parameters -------------------------------------------------------------------

    @Test
    void invalidQueryParameter() throws Exception {
        expect(get("/api/v1/scores/fairness").param("days", "0"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("days"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than or equal to 1"));
    }

    @Test
    void unparseableQueryParameterDoesNotLeakJavaTypes() throws Exception {
        expect(get("/api/v1/scores/fairness").param("start", "not-a-date"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("start"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("java."))));
    }

    @Test
    void invalidPathVariable() throws Exception {
        expect(get("/api/v1/scores/engineers/0"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("engineerId"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than 0"));
    }

    @Test
    void unparseablePathVariable() throws Exception {
        expect(get("/api/v1/teams/abc"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid value"));
    }

    @Test
    void missingRequiredParameter() throws Exception {
        expect(get("/api/v1/oncall").param("end", "2026-09-30"), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("start"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));
    }

    // --- Rules the services enforce ------------------------------------------------------------------

    @Test
    void unknownReferenceIsAFieldError() throws Exception {
        expect(json(post("/api/v1/engineers"), engineer("asha@example.com", "Nope")), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.detail").value("Unknown team 'Nope'; create it via /api/v1/teams"))
                .andExpect(jsonPath("$.errors[0].field").value("team"));
    }

    @Test
    void missingEngineerInABodyIsNotA404() throws Exception {
        expect(json(post("/api/v1/alerts"), "{\"engineerId\": 999999, \"severity\": \"P2\"}"), 400,
                "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("engineerId"))
                .andExpect(jsonPath("$.errors[0].message").value("Engineer 999999 doesn't exist"));
    }

    @Test
    void duplicateTeamNameNamesTheField() throws Exception {
        teams.save(new Team("Error SRE", "Asia/Kolkata"));

        expect(json(post("/api/v1/teams"), "{\"name\": \"Error SRE\"}"), 409, "CONFLICT")
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Team 'Error SRE' already exists"));
    }

    @Test
    void duplicateEmailIsCaseInsensitive() throws Exception {
        Engineer asha = engineer("Asha", "asha.errors@example.com", teams.save(new Team("Error SRE", "Asia/Kolkata")));

        expect(json(post("/api/v1/engineers"), engineer("ASHA.Errors@example.com", "Error SRE")), 409, "CONFLICT")
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Engineer " + asha.getId() + " already uses ASHA.Errors@example.com"));
    }

    @Test
    void conflictWithoutAFieldHasNoFieldErrors() throws Exception {
        Team team = teams.save(new Team("Error SRE", "Asia/Kolkata"));
        engineer("Asha", "asha.errors@example.com", team);

        expect(delete("/api/v1/teams/{id}", team.getId()), 409, "CONFLICT")
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    // --- Dependencies and the container --------------------------------------------------------------

    @Test
    void unreachableScorer() throws Exception {
        // scorer.base-url points at a closed port in the test profile
        expect(get("/api/v1/scores/fairness").param("days", "30"), 503, "UPSTREAM_UNAVAILABLE")
                .andExpect(jsonPath("$.detail").value("Scoring service is unavailable"));
    }

    @Test
    void errorForwardedByTheServletContainer() throws Exception {
        // As after a real forward: the container's error attributes, plus the id the filter stored on the
        // original request (the filter itself doesn't run again for an error dispatch)
        expect(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/teams")
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "original-request-id"),
                400, "BAD_REQUEST")
                .andExpect(jsonPath("$.instance").value("/api/v1/teams"))
                .andExpect(jsonPath("$.detail").value("Bad Request"))
                .andExpect(jsonPath("$.requestId").value("original-request-id"));
    }

    @Test
    void errorPathCalledDirectlyIsNotFound() throws Exception {
        expect(get("/error"), 404, "NOT_FOUND");
    }

    // -------------------------------------------------------------------------------------------------

    private ResultActions expect(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        return mvc.perform(request)
                .andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String engineer(String email, String team) {
        return "{\"name\": \"Asha\", \"email\": \"%s\", \"team\": \"%s\"}".formatted(email, team);
    }

    private Engineer engineer(String name, String email, Team team) {
        Engineer engineer = new Engineer();
        engineer.setName(name);
        engineer.setEmail(email);
        engineer.setTeam(team);
        return engineers.save(engineer);
    }
}
