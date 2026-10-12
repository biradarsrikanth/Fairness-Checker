package com.example.fairnesstracker.support;

import com.example.fairnesstracker.security.SecurityConfig;
import com.example.fairnesstracker.security.SecurityErrorResponses;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * For {@code @WebMvcTest} slices: the application's real filter chain instead of Boot's default one (which
 * would require CSRF tokens and basic auth). Bearer tokens are always rejected; sign in with
 * {@code @WithMockUser} instead.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, SecurityErrorResponses.class})
public class SliceSecurity {

    @Bean
    JwtDecoder rejectingJwtDecoder() {
        return token -> {
            throw new BadJwtException("Slice tests don't decode tokens");
        };
    }
}
