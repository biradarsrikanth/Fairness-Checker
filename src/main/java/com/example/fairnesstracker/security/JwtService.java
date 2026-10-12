package com.example.fairnesstracker.security;

import com.example.fairnesstracker.user.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues access tokens. Claims: {@code sub} (user id), {@code email}, {@code role}, plus {@code iss},
 * {@code aud}, {@code iat}, {@code exp} and a unique {@code jti}. Team access isn't in the token: it's
 * looked up per request, so a change applies at once rather than when the token expires.
 */
@Service
public class JwtService {

    public static final String EMAIL_CLAIM = "email";
    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return new AccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }
}
