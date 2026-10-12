package com.example.fairnesstracker.security;

import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.Role;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final JwtProperties PROPERTIES = properties("test-jwt-secret-0123456789abcdef0123456789",
            "fairness-checker", "fairness-web");

    private final JwtConfig config = new JwtConfig();
    private final JwtService service = service(PROPERTIES);
    private final AppUser user = user();

    @Test
    void issuesATokenTheDecoderAccepts() {
        AccessToken token = service.issue(user);

        Jwt jwt = decoderAt(NOW.plusSeconds(60)).decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(JwtService.EMAIL_CLAIM)).isEqualTo("asha@example.com");
        assertThat(jwt.getClaimAsString(JwtService.ROLE_CLAIM)).isEqualTo("MANAGER");
        assertThat(jwt.getClaimAsString(JwtClaimNames.ISS)).isEqualTo("fairness-checker");
        assertThat(jwt.getAudience()).containsExactly("fairness-web");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void everyTokenHasItsOwnId() {
        assertThat(decode(service.issue(user)).getId()).isNotEqualTo(decode(service.issue(user)).getId());
    }

    @Test
    void expiresAfterItsLifetimePlusTheClockSkew() {
        String token = service.issue(user).value();
        Instant expiry = NOW.plus(Duration.ofMinutes(15));

        assertThat(decoderAt(expiry.plus(JwtConfig.CLOCK_SKEW)).decode(token)).isNotNull();
        assertRejected(decoderAt(expiry.plus(JwtConfig.CLOCK_SKEW).plusSeconds(1)), token);
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String forged = service(properties("another-secret-0123456789abcdef0123456789", "fairness-checker",
                "fairness-web")).issue(user).value();

        assertRejected(decoderAt(NOW), forged);
    }

    @Test
    void rejectsAnotherIssuerOrAudience() {
        String secret = PROPERTIES.secret();
        assertRejected(decoderAt(NOW), service(properties(secret, "someone-else", "fairness-web")).issue(user).value());
        assertRejected(decoderAt(NOW), service(properties(secret, "fairness-checker", "another-app")).issue(user).value());
    }

    @Test
    void rejectsAModifiedPayload() {
        String[] parts = service.issue(user).value().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"MANAGER\"", "\"ADMIN\"");
        String tampered = parts[0] + "." + base64(payload) + "." + parts[2];

        assertRejected(decoderAt(NOW), tampered);
    }

    @Test
    void rejectsAnUnsignedToken() {
        String claims = new String(Base64.getUrlDecoder().decode(service.issue(user).value().split("\\.")[1]),
                StandardCharsets.UTF_8);
        String unsigned = base64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + base64(claims) + ".";

        assertRejected(decoderAt(NOW), unsigned);
    }

    @Test
    void rejectsATokenOfAnotherType() {
        // Same secret and claims, but typed as something other than an access token
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("fairness-checker").audience(List.of("fairness-web"))
                .subject("42").issuedAt(NOW).expiresAt(NOW.plusSeconds(600)).build();
        String token = config.jwtEncoder(PROPERTIES).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("refresh+jwt").build(), claims)).getTokenValue();

        assertRejected(decoderAt(NOW), token);
    }

    @Test
    void settingsAreCheckedAtStartup() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(PROPERTIES)).isEmpty();
        assertThat(validator.validate(properties("too-short", "i", "a")))
                .extracting(v -> v.getPropertyPath() + " " + v.getMessage())
                .containsExactly("secret must be at least 32 characters");
        assertThat(validator.validate(new JwtProperties(PROPERTIES.secret(), "i", "a", Duration.ofDays(1))))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("accessTokenTtl");
    }

    private JwtService service(JwtProperties properties) {
        return new JwtService(config.jwtEncoder(properties), properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JwtDecoder decoderAt(Instant now) {
        return config.jwtDecoder(PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Jwt decode(AccessToken token) {
        return decoderAt(NOW).decode(token.value());
    }

    private static void assertRejected(JwtDecoder decoder, String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static JwtProperties properties(String secret, String issuer, String audience) {
        return new JwtProperties(secret, issuer, audience, Duration.ofMinutes(15));
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha@example.com", "Asha", Role.MANAGER);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }
}
