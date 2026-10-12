package com.example.fairnesstracker.common.exception;

import com.example.fairnesstracker.team.TeamController;
import com.example.fairnesstracker.team.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A bug must not leak internals (hosts, credentials, SQL) to the caller
@WebMvcTest(TeamController.class)
@ActiveProfiles("test")
class UnexpectedErrorTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TeamService teamService;

    @Test
    void answersWithAGenericInternalError() throws Exception {
        when(teamService.getById(1L))
                .thenThrow(new IllegalStateException("connect to db-prod.internal:5432 failed, password=hunter2"));

        mvc.perform(get("/api/v1/teams/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.INTERNAL_DETAIL))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("db-prod"))));
    }
}
