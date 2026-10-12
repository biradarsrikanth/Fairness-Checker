package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.user.AppUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens: 256 random bits the client keeps in an httpOnly cookie; only their SHA-256 is stored.
 * <ul>
 *   <li>Signing in starts a family; every refresh replaces the presented token with a new one in the same
 *       family (rotation), so a token works once.</li>
 *   <li>Presenting a token that was already replaced means two parties hold it, e.g. a stolen cookie: the
 *       whole family is revoked and both have to sign in again. Within {@code reuseGrace} of the rotation
 *       this is taken to be the same browser racing itself instead, and gets a new token too.</li>
 *   <li>Signing out revokes the family; changing a password or disabling a user revokes all of theirs.</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenService {

    // Longer values can't be ours; not worth hashing
    private static final int MAX_TOKEN_LENGTH = 128;

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    /** The user the presented token belonged to, and the token that replaces it. */
    public record Rotation(AppUser user, IssuedRefreshToken token) {
    }

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Signing in: a token that starts a new family. */
    @Transactional
    public IssuedRefreshToken issue(AppUser user, ClientInfo client) {
        return create(user, UUID.randomUUID(), client).issued();
    }

    /**
     * Refreshing: checks the presented token and replaces it. Revocations made here must survive the 401
     * that follows them, hence no rollback for {@link InvalidRefreshTokenException}.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presented, ClientInfo client) {
        RefreshToken token = find(presented).orElseThrow(InvalidRefreshTokenException::invalid);
        Instant now = clock.instant();
        AppUser user = token.getUser();

        if (!user.isEnabled()) {
            repository.revokeFamily(token.getFamilyId(), now);
            throw InvalidRefreshTokenException.invalid();
        }
        if (token.isRevoked()) {
            // Signed out, revoked, or the family already ended: nothing left to protect
            if (token.getReplacedBy() == null || !familyIsActive(token, now)) {
                throw InvalidRefreshTokenException.invalid();
            }
            if (rotatedWithinGrace(token, now)) {
                log.debug("Refresh token of user {} reused {} after rotation; within the grace period",
                        user.getId(), Duration.between(token.getRevokedAt(), now));
                return new Rotation(user, create(user, token.getFamilyId(), client).issued());
            }
            int revoked = repository.revokeFamily(token.getFamilyId(), now);
            log.warn("Refresh token reuse for user {}: revoked family {} ({} active tokens)",
                    user.getId(), token.getFamilyId(), revoked);
            throw InvalidRefreshTokenException.reused();
        }
        if (token.isExpiredAt(now)) {
            throw InvalidRefreshTokenException.invalid();
        }

        NewToken replacement = create(user, token.getFamilyId(), client);
        token.rotatedTo(replacement.entity(), now);
        return new Rotation(user, replacement.issued());
    }

    /** Signing out: ends the session the token belongs to. Unknown or missing tokens are ignored. */
    @Transactional
    public void revokeFamily(String presented) {
        find(presented).ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Ends every session of a user, e.g. after a password change or when the user is disabled. */
    @Transactional
    public int revokeAll(AppUser user) {
        return repository.revokeAllForUser(user.getId(), clock.instant());
    }

    // Expired tokens can't be used or reused any more; keeping them only grows the table
    @Scheduled(cron = "${auth.refresh-token.purge-cron:0 30 3 * * *}")
    @Transactional
    public int purgeExpired() {
        int deleted = repository.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
        return deleted;
    }

    private Optional<RefreshToken> find(String presented) {
        if (presented == null || presented.isBlank() || presented.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(presented));
    }

    private boolean rotatedWithinGrace(RefreshToken token, Instant now) {
        return !now.isAfter(token.getRevokedAt().plus(properties.reuseGrace()));
    }

    // False once the family was signed out, revoked or has expired
    private boolean familyIsActive(RefreshToken token, Instant now) {
        return repository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(token.getFamilyId(), now);
    }

    private NewToken create(AppUser user, UUID familyId, ClientInfo client) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        RefreshToken entity = repository.save(new RefreshToken(user, hash(value), familyId, now,
                now.plus(properties.ttl()), client.ip(), client.userAgent()));
        return new NewToken(entity, value);
    }

    // The raw value exists only here and in the response; the database gets its hash
    private record NewToken(RefreshToken entity, String rawValue) {
        IssuedRefreshToken issued() {
            return new IssuedRefreshToken(rawValue, entity.getExpiresAt());
        }
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
