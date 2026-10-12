package com.example.fairnesstracker.engineer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(EngineerController.class)
@ActiveProfiles("test")
public class EngineerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EngineerService engineerService;

    @Test
    public void postInvalidEngineer_returns400() throws Exception {

        String badJson = """
                {
                    "name": "",
                    "email": "not-an-email",
                    "team": ""
                }
                """;

        mvc.perform(post("/api/v1/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }
}

