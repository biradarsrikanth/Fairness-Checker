package com.example.fairnesstracker.security;

import com.example.fairnesstracker.common.exception.ApiError;
import com.example.fairnesstracker.common.exception.ErrorCode;
import com.example.fairnesstracker.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

/**
 * Errors raised by the security filters happen before any controller, so @RestControllerAdvice never sees
 * them. These write the same {@link ApiError} body the rest of the API returns.
 */
@Component
public class SecurityErrorResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public SecurityErrorResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // No token, or one that failed to verify (expired, bad signature, wrong audience...)
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean badToken = ex instanceof InvalidBearerTokenException;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        write(request, response, ErrorCode.UNAUTHENTICATED, badToken
                ? "Access token is invalid or expired; refresh it or sign in again"
                : "Sign in to use this endpoint");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, ErrorCode.FORBIDDEN, ForbiddenException.DEFAULT_DETAIL);
    }

    void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiError.of(code.status(), code, detail, List.of(), request));
    }
}
