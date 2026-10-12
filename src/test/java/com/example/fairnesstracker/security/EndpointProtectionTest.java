package com.example.fairnesstracker.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Every endpoint of the application, called without a token, must answer 401 unless it's on the public
 * list below. A new endpoint is covered automatically; making one public means changing this list too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndpointProtectionTest {

    private static final Set<String> PUBLIC = Set.of(
            "GET /",
            "POST /api/webhook/pagerduty",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/logout");

    @Autowired private MockMvc mvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @Test
    void everyNonPublicEndpointNeedsAToken() throws Exception {
        List<String> checked = new ArrayList<>();
        List<String> open = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : mappings.getHandlerMethods().entrySet()) {
            if (!mapping.getValue().getBeanType().getPackageName().startsWith("com.example.fairnesstracker")) {
                continue;
            }
            for (String pattern : mapping.getKey().getPatternValues()) {
                if (pattern.contains("error")) {
                    continue;
                }
                for (var method : mapping.getKey().getMethodsCondition().getMethods()) {
                    String endpoint = method.name() + " " + pattern;
                    String path = pattern.replaceAll("\\{[^}]+}", "1");
                    int status = mvc.perform(request(HttpMethod.valueOf(method.name()), path))
                            .andReturn().getResponse().getStatus();
                    checked.add(endpoint);
                    if (status != 401 && !PUBLIC.contains(endpoint)) {
                        open.add(endpoint + " -> " + status);
                    }
                }
            }
        }

        assertThat(open).as("endpoints reachable without a token").isEmpty();
        // The sweep really saw the application's endpoints
        assertThat(checked).contains("GET /api/v1/users", "DELETE /api/v1/teams/{id}", "GET /api/v1/audit")
                .hasSizeGreaterThan(30);
    }
}
