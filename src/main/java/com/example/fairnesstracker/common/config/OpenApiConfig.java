package com.example.fairnesstracker.common.config;

import com.example.fairnesstracker.common.exception.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
                .description("On-call fairness and burnout tracking for PagerDuty. Every error is an ApiError "
                        + "(RFC 9457 problem details, application/problem+json); branch on its code."));
    }

    // Documents ApiError as every operation's error response, so generated clients get a typed error model
    @Bean
    public OpenApiCustomizer problemDetailsResponses() {
        return openApi -> {
            ModelConverters.getInstance(true).readAll(ApiError.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            ApiResponse error = new ApiResponse()
                    .description("Error (RFC 9457 problem details)")
                    .content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
            openApi.getPaths().values().forEach(path -> path.readOperations()
                    .forEach(operation -> operation.getResponses().addApiResponse("default", error)));
        };
    }
}
