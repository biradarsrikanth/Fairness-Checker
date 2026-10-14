package com.example.fairnesstracker.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless: every request carries its access token ({@code Authorization: Bearer ...}); there are no
 * sessions, so no CSRF tokens either. The refresh cookie that /auth/refresh and /auth/logout read is
 * SameSite=Strict and scoped to /api/v1/auth, so other sites can't make a browser send it.
 *
 * <p>Routes are closed unless listed here. Who may do what beyond "signed in" is decided per endpoint, with
 * {@code @PreAuthorize} for roles and {@link AccessPolicy} for the teams and engineers a user may see.
 */
@Configuration(proxyBeanMethods = false)
// Not in a non-web run (ops/migrate-db.sh starts the app without a web server, where HttpSecurity doesn't exist)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorResponses errors, AccessPolicy access)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(routes -> routes
                        // Liveness text, error pages, health probes and metrics scraping
                        .requestMatchers("/", "/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        // PagerDuty authenticates with its HMAC signature, not a token
                        .requestMatchers(HttpMethod.POST, "/api/webhook/pagerduty").permitAll()
                        // Getting a token; logout only needs the refresh cookie
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        // API docs; off in production with API_DOCS_ENABLED=false
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                // Both run once the token is verified: first the account must still be active, then a password
                // that must be changed limits what it may do. Each is placed after the one before it: placed
                // "before" the second, the first would share the token filter's position and could run ahead of it
                .addFilterAfter(new ActiveAccountFilter(access, errors), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new PasswordChangeRequiredFilter(errors), ActiveAccountFilter.class);
        return http.build();
    }

    // The "role" claim becomes the ROLE_ADMIN/ROLE_MANAGER/ROLE_ENGINEER authority; the name is the user id
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
