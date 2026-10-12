package com.example.fairnesstracker.alert;

import org.springframework.security.test.context.support.WithMockUser;
import com.example.fairnesstracker.support.SliceSecurity;
import org.springframework.context.annotation.Import;
import com.example.fairnesstracker.engineer.EngineerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertEventController.class)
@ActiveProfiles("test")
// Endpoint behaviour, signed in as an admin; access rules are covered by the security tests
@WithMockUser(roles = "ADMIN")
@Import(SliceSecurity.class)
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

        mvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("engineerId"));
    }
}