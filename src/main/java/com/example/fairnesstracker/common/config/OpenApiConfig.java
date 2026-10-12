package com.example.fairnesstracker.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spec at /v3/api-docs, Swagger UI at /swagger-ui.html; both switched off with API_DOCS_ENABLED=false.
// The frontend generates its typed API client from this spec, one service per @Tag
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fairnessCheckerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Fairness Checker API")
                .version("v1")
                .description("On-call fairness and burnout tracking for PagerDuty. Errors use the ApiError shape."));
    }
}
