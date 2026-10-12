package com.example.fairnesstracker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Signs and verifies access tokens with one shared secret (HS256): this service is both the issuer and the
 * only consumer. The decoder accepts nothing but HS256 tokens of the "JWT" type from our issuer, for our
 * audience, with a subject, inside their lifetime.
 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    // Tolerated difference between clocks, e.g. a token checked by another instance a moment early
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(signingKey(properties)).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator lifetime = new JwtTimestampValidator(CLOCK_SKEW);
        lifetime.setClock(clock);
        lifetime.setAllowEmptyExpiryClaim(false);
        JwtTypeValidator type = JwtTypeValidator.jwt();
        type.setAllowEmpty(false);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                lifetime,
                type,
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, Objects::nonNull)));
        return decoder;
    }

    private static SecretKey signingKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
