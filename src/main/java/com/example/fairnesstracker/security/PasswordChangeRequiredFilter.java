package com.example.fairnesstracker.security;

import com.example.fairnesstracker.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * A user who must change their password (e.g. the bootstrap admin) gets a token marked
 * {@value JwtService#PASSWORD_CHANGE_CLAIM}; with it they may only see who they are, change the password,
 * or sign out. Not a bean, so it isn't also registered as a plain servlet filter.
 */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    static final Set<String> ALLOWED = Set.of("/api/v1/auth/me", "/api/v1/auth/change-password", "/api/v1/auth/logout");

    private final SecurityErrorResponses errors;

    PasswordChangeRequiredFilter(SecurityErrorResponses errors) {
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(JwtService.PASSWORD_CHANGE_CLAIM))
                && !ALLOWED.contains(request.getRequestURI().substring(request.getContextPath().length()))) {
            errors.write(request, response, ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "Change your password first: POST /api/v1/auth/change-password");
            return;
        }
        chain.doFilter(request, response);
    }
}
