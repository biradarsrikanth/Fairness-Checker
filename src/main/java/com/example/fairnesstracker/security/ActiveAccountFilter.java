package com.example.fairnesstracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns away tokens of accounts disabled or deleted since the token was issued, instead of honouring them
 * until they expire. The check is {@link AccessPolicy#scope()}, which the request's endpoint would load
 * anyway and keeps for the rest of the request, so it costs no extra query. Not a bean, so it isn't also
 * registered as a plain servlet filter.
 */
class ActiveAccountFilter extends OncePerRequestFilter {

    private final AccessPolicy access;
    private final SecurityErrorResponses errors;

    ActiveAccountFilter(AccessPolicy access, SecurityErrorResponses errors) {
        this.access = access;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken) {
            try {
                access.scope();
            } catch (InactiveAccountException e) {
                errors.write(request, response, e.getCode(), e.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
