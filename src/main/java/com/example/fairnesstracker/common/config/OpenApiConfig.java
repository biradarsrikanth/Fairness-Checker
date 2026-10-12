package com.example.fairnesstracker.common.config;

import com.example.fairnesstracker.common.exception.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spec at /v3/api-docs, Swagger UI at /swagger-ui.html; both switched off with API_DOCS_ENABLED=false.
// The frontend generates its typed API client from this spec, one service per @Tag
@Configuration
public class OpenApiConfig {

    /** Every operation needs it unless it says otherwise (@SecurityRequirements with no value). */
    public static final String BEARER = "bearerAuth";
    /** The httpOnly refresh cookie that login sets; used by /auth/refresh and /auth/logout. */
    public static final String REFRESH_COOKIE = "refreshCookie";

    @Bean
    public OpenAPI fairnessCheckerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fairness Checker API")
                        .version("v1")
                        .description("On-call fairness and burnout tracking for PagerDuty. Sign in with "
                                + "POST /api/v1/auth/login, then send the accessToken as a Bearer token (in Swagger "
                                + "UI: Authorize). Every error is an ApiError (RFC 9457 problem details, "
                                + "application/problem+json); branch on its code."))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("accessToken from /api/v1/auth/login or /api/v1/auth/refresh; "
                                        + "valid for 15 minutes"))
                        .addSecuritySchemes(REFRESH_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("fairness_refresh")
                                .description("httpOnly cookie set by login; the browser sends it by itself")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
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
