package com.example.fairnesstracker.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/** The signed-in user of the current request, as the access token names them. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Empty without a token (anonymous, scheduled work, or a mock user in tests). */
    public static Optional<Long> id() {
        return token().map(jwt -> Long.valueOf(jwt.getName()));
    }

    public static Optional<String> email() {
        return token().map(jwt -> jwt.getToken().getClaimAsString(JwtService.EMAIL_CLAIM));
    }

    private static Optional<JwtAuthenticationToken> token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt ? Optional.of(jwt) : Optional.empty();
    }
}
