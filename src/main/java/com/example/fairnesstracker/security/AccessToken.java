package com.example.fairnesstracker.security;

import java.time.Instant;

/** A signed JWT to send as {@code Authorization: Bearer <value>} until {@code expiresAt}. */
public record AccessToken(String value, Instant expiresAt) {
}
