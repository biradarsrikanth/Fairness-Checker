package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.auth.RefreshTokenService.IssuedRefreshToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Clock;
import java.time.Duration;

/** Writes, clears and reads the refresh token cookie. */
@Component
class RefreshCookies {

    private final RefreshCookieProperties properties;
    private final Clock clock;

    RefreshCookies(RefreshCookieProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    String issue(IssuedRefreshToken token) {
        return cookie(token.value(), Duration.between(clock.instant(), token.expiresAt())).toString();
    }

    String clear() {
        return cookie("", Duration.ZERO).toString();
    }

    String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.name());
        return cookie != null ? cookie.getValue() : null;
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(maxAge)
                .build();
    }
}
