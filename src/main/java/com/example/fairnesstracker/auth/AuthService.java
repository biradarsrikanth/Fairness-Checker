package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.auth.RefreshTokenService.IssuedRefreshToken;
import com.example.fairnesstracker.auth.RefreshTokenService.Rotation;
import com.example.fairnesstracker.auth.dto.AuthDtos.TeamRef;
import com.example.fairnesstracker.auth.dto.AuthDtos.UserResponse;
import com.example.fairnesstracker.common.exception.ValidationException;
import com.example.fairnesstracker.security.AccessToken;
import com.example.fairnesstracker.security.JwtService;
import com.example.fairnesstracker.team.Team;
import com.example.fairnesstracker.team.TeamRepository;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Signing in and out, refreshing, and changing the password. Each successful step returns a new session. */
@Slf4j
@Service
public class AuthService {

    /** Access token for the response body, refresh token for the cookie. */
    public record Session(AccessToken accessToken, IssuedRefreshToken refreshToken, UserResponse user) {
    }

    private final AppUserRepository users;
    private final TeamRepository teams;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;
    // Checked when the email is unknown, so that takes as long as a wrong password
    private final String unknownUserHash;

    public AuthService(AppUserRepository users, TeamRepository teams, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokens, Clock clock) {
        this.users = users;
        this.teams = teams;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.unknownUserHash = passwordEncoder.encode("unknown-user-placeholder");
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session login(String email, String password, ClientInfo client) {
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, unknownUserHash);
            throw AuthenticationFailedException.invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw AuthenticationFailedException.invalidCredentials();
        }
        if (!user.isEnabled()) {
            throw AuthenticationFailedException.disabled();
        }

        Instant now = clock.instant();
        user.setLastLoginAt(now);
        // Hashes made with older settings are replaced while the plain password is at hand
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPassword(passwordEncoder.encode(password), user.getPasswordChangedAt());
        }
        log.info("User {} signed in", user.getId());
        return session(user, refreshTokens.issue(user, client));
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Session refresh(String refreshToken, ClientInfo client) {
        Rotation rotation = refreshTokens.rotate(refreshToken, client);
        return session(rotation.user(), rotation.token());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revokeFamily(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse me(long userId) {
        return toResponse(find(userId));
    }

    /** Ends every other session; the caller gets a fresh one without the password-change mark. */
    @Transactional
    public Session changePassword(long userId, String currentPassword, String newPassword, ClientInfo client) {
        AppUser user = find(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ValidationException.onField("currentPassword", "is incorrect");
        }
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ValidationException.onField("newPassword", "must be at most 72 bytes");
        }
        if (newPassword.equals(currentPassword)) {
            throw ValidationException.onField("newPassword", "must differ from the current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword), clock.instant());
        user.setMustChangePassword(false);
        int ended = refreshTokens.revokeAll(user);
        log.info("User {} changed their password; ended {} sessions", user.getId(), ended);
        return session(user, refreshTokens.issue(user, client));
    }

    private AppUser find(long userId) {
        return users.findWithAccessById(userId).orElseThrow(AuthenticationFailedException::unknownUser);
    }

    private Session session(AppUser user, IssuedRefreshToken refreshToken) {
        return new Session(jwtService.issue(user), refreshToken, toResponse(user));
    }

    private UserResponse toResponse(AppUser user) {
        Stream<Team> visible = switch (user.getRole()) {
            case ADMIN -> teams.findAll(Sort.by("name")).stream();
            case MANAGER -> user.getTeams().stream();
            case ENGINEER -> Stream.of(user.getEngineer().getTeam());
        };
        List<TeamRef> teamRefs = visible.sorted(Comparator.comparing(Team::getName))
                .map(team -> new TeamRef(team.getId(), team.getName()))
                .toList();
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getEngineer() != null ? user.getEngineer().getId() : null, teamRefs,
                user.isMustChangePassword());
    }
}
