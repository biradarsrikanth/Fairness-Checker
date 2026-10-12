package com.example.fairnesstracker.engineer;

import org.springframework.security.test.context.support.WithMockUser;
import com.example.fairnesstracker.support.SliceSecurity;
import org.springframework.context.annotation.Import;
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
// Endpoint behaviour, signed in as an admin; access rules are covered by the security tests
@WithMockUser(roles = "ADMIN")
@Import(SliceSecurity.class)
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

