package com.example.fairnesstracker.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The cookie that carries the refresh token: httpOnly (scripts can't read it), sent only to the auth
 * endpoints, and SameSite=Strict so other sites can't make the browser send it. {@code secure} stays true
 * except for plain-HTTP setups other than localhost (browsers treat localhost as secure).
 */
@Validated
@ConfigurationProperties("auth.refresh-cookie")
public record RefreshCookieProperties(
        @NotBlank String name,
        @NotBlank String path,
        boolean secure,
        @Pattern(regexp = "Strict|Lax") String sameSite
) {
}
