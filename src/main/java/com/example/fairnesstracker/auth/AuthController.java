package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.auth.AuthService.Session;
import com.example.fairnesstracker.auth.dto.AuthDtos.ChangePasswordRequest;
import com.example.fairnesstracker.auth.dto.AuthDtos.LoginRequest;
import com.example.fairnesstracker.auth.dto.AuthDtos.TokenResponse;
import com.example.fairnesstracker.auth.dto.AuthDtos.UserResponse;
import com.example.fairnesstracker.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessions. The access token comes back in the body and is kept in memory by the client; the refresh
 * token is set as an httpOnly cookie that only these endpoints receive.
 */
@Tag(name = "Auth", description = "Sign in and out, refresh the access token, change the password")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;
    private final LoginRateLimiter rateLimiter;

    public AuthController(AuthService authService, RefreshCookies cookies, LoginRateLimiter rateLimiter) {
        this.authService = authService;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
    }

    @Operation(summary = "Sign in", description = "Returns an access token and sets the refresh cookie. "
            + "By default, 5 wrong passwords in a row lock the account for 15 minutes (423), and a client IP "
            + "gets at most 10 attempts per minute (429). Both come with Retry-After.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        rateLimiter.check(httpRequest.getRemoteAddr());
        return respond(authService.login(request.email(), request.password(), client(httpRequest)));
    }

    @Operation(summary = "Get a new access token",
            description = "Uses the refresh cookie, which is replaced. On failure the cookie is cleared: sign in again.")
    @SecurityRequirement(name = OpenApiConfig.REFRESH_COOKIE)
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return respond(authService.refresh(cookies.read(request), client(request)));
        } catch (InvalidRefreshTokenException e) {
            // The error body is written by the exception handler; the header set here stays on the response
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear());
            throw e;
        }
    }

    @Operation(summary = "Sign out", description = "Ends this session (every token from this sign-in) and clears the cookie.")
    @SecurityRequirement(name = OpenApiConfig.REFRESH_COOKIE)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookies.read(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    @Operation(summary = "The signed-in user, with the teams they can see")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(userId(jwt));
    }

    @Operation(summary = "Change the password",
            description = "Signs out every other session and returns a fresh one.")
    @PostMapping("/change-password")
    public ResponseEntity<TokenResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody ChangePasswordRequest request,
                                                        HttpServletRequest httpRequest) {
        return respond(authService.changePassword(userId(jwt), request.currentPassword(), request.newPassword(),
                client(httpRequest)));
    }

    private ResponseEntity<TokenResponse> respond(Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken()))
                // Tokens must never be cached by a browser or proxy
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(session.accessToken().value(), session.accessToken().expiresAt(),
                        session.user()));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
