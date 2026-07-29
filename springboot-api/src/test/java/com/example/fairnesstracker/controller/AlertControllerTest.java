package com.example.fairnesstracker.controller;

import com.example.fairnesstracker.service.AlertService;
import com.example.fairnesstracker.service.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertEventController.class)
class AlertEventControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlertService alertService;

    @MockitoBean
    private EngineerService engineerService;

    @Test
    void postInvalidAlert_returns400() throws Exception {

        String badJson = """
                {
                    "engineerId": "",
                    "engineerName": "",
                    "severity": "P5"
                }
                """;

        mvc.perform(post("/api/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error")
                        .value("VALIDATION_FAILED"));
    }
}