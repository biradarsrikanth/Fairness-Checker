package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.audit.AuditService;
import com.example.fairnesstracker.auth.RefreshTokenService.IssuedRefreshToken;
import com.example.fairnesstracker.auth.RefreshTokenService.Rotation;
import com.example.fairnesstracker.common.exception.ErrorCode;
import com.example.fairnesstracker.support.MutableClock;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DataJpaTest
@Import({RefreshTokenService.class, AuditService.class, RefreshTokenServiceTest.Config.class})
class RefreshTokenServiceTest {

    static final Instant START = Instant.parse("2026-10-12T10:00:00Z");
    static final Duration TTL = Duration.ofDays(7);
    static final Duration GRACE = Duration.ofSeconds(10);
    static final ClientInfo BROWSER = new ClientInfo("203.0.113.7", "Firefox");

    @TestConfiguration
    static class Config {
        @Bean
        MutableClock clock() {
            return new MutableClock(START);
        }

        @Bean
        RefreshTokenProperties refreshTokenProperties() {
            return new RefreshTokenProperties(TTL, GRACE);
        }
    }

    @Autowired private RefreshTokenService service;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private AppUserRepository users;
    @Autowired private MutableClock clock;
    @Autowired private EntityManager entityManager;

    private AppUser asha;

    @BeforeEach
    void setUp() {
        clock.set(START);
        asha = users.save(user("asha@example.com"));
    }

    // --- Issuing -------------------------------------------------------------------------------------

    @Test
    void storesOnlyTheHashOfTheToken() {
        IssuedRefreshToken issued = service.issue(asha, BROWSER);

        RefreshToken stored = stored(issued);
        assertThat(issued.value()).hasSize(43).doesNotContain("=", "+", "/");
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(issued.value());
        assertThat(stored.getExpiresAt()).isEqualTo(issued.expiresAt()).isEqualTo(START.plus(TTL));
        assertThat(stored.getCreatedIp()).isEqualTo("203.0.113.7");
        assertThat(stored.getUserAgent()).isEqualTo("Firefox");
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void everySignInStartsANewFamily() {
        assertThat(stored(service.issue(asha, BROWSER)).getFamilyId())
                .isNotEqualTo(stored(service.issue(asha, BROWSER)).getFamilyId());
    }

    // --- Rotation ------------------------------------------------------------------------------------

    @Test
    void refreshingReplacesTheTokenAndRestartsItsLifetime() {
        IssuedRefreshToken first = service.issue(asha, BROWSER);
        clock.advance(Duration.ofDays(3));

        Rotation rotation = service.rotate(first.value(), BROWSER);

        assertThat(rotation.user().getId()).isEqualTo(asha.getId());
        assertThat(rotation.token().value()).isNotEqualTo(first.value());
        assertThat(rotation.token().expiresAt()).isEqualTo(clock.instant().plus(TTL));
        RefreshToken old = stored(first);
        RefreshToken replacement = stored(rotation.token());
        assertThat(old.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(old.getReplacedBy().getId()).isEqualTo(replacement.getId());
        assertThat(replacement.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(replacement.isRevoked()).isFalse();
    }

    @Test
    void aReplacedTokenWorksAgainWithinTheGracePeriod() {
        // Two tabs refresh with the same cookie at the same moment: both get a session
        IssuedRefreshToken first = service.issue(asha, BROWSER);
        IssuedRefreshToken second = service.rotate(first.value(), BROWSER).token();
        clock.advance(GRACE);

        IssuedRefreshToken third = service.rotate(first.value(), BROWSER).token();

        assertThat(third.value()).isNotEqualTo(second.value());
        assertThat(stored(third).getFamilyId()).isEqualTo(stored(first).getFamilyId());
        assertThat(service.rotate(second.value(), BROWSER).token()).isNotNull();
    }

    @Test
    void aReplacedTokenUsedAfterTheGracePeriodEndsTheWholeSession() {
        IssuedRefreshToken stolen = service.issue(asha, BROWSER);
        IssuedRefreshToken current = service.rotate(stolen.value(), BROWSER).token();
        clock.advance(GRACE.plusSeconds(1));

        assertRejected(stolen, ErrorCode.REFRESH_TOKEN_REUSED);
        // The legitimate holder is signed out too: nobody knows which of the two is the thief
        assertRejected(current, ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(stored(current).getRevokedAt()).isEqualTo(clock.instant());
    }

    @Test
    void reuseDoesNotTouchOtherSessions() {
        IssuedRefreshToken stolen = service.issue(asha, BROWSER);
        service.rotate(stolen.value(), BROWSER);
        IssuedRefreshToken laptop = service.issue(asha, new ClientInfo("198.51.100.1", "Chrome"));
        clock.advance(Duration.ofMinutes(5));

        assertRejected(stolen, ErrorCode.REFRESH_TOKEN_REUSED);
        assertThat(service.rotate(laptop.value(), BROWSER).token()).isNotNull();
    }

    @Test
    void anExpiredTokenIsRejected() {
        IssuedRefreshToken token = service.issue(asha, BROWSER);
        clock.advance(TTL);

        assertRejected(token, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void unknownBlankAndOversizedTokensAreRejected() {
        for (String value : new String[]{"not-a-token", "", null, "x".repeat(10_000)}) {
            assertThatThrownBy(() -> service.rotate(value, BROWSER))
                    .isInstanceOfSatisfying(InvalidRefreshTokenException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID));
        }
    }

    @Test
    void aDisabledUserCantRefreshAndLosesTheSession() {
        IssuedRefreshToken token = service.issue(asha, BROWSER);
        asha.setEnabled(false);
        users.saveAndFlush(asha);

        assertRejected(token, ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(stored(token).isRevoked()).isTrue();
    }

    // --- Signing out ---------------------------------------------------------------------------------

    @Test
    void signingOutEndsTheSessionEvenForATokenStillInItsGracePeriod() {
        IssuedRefreshToken first = service.issue(asha, BROWSER);
        IssuedRefreshToken second = service.rotate(first.value(), BROWSER).token();

        service.revokeFamily(second.value());
        clock.advance(Duration.ofSeconds(1));

        assertRejected(second, ErrorCode.REFRESH_TOKEN_INVALID);
        assertRejected(first, ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void signingOutWithAnUnknownTokenDoesNothing() {
        IssuedRefreshToken token = service.issue(asha, BROWSER);

        service.revokeFamily("not-a-token");
        service.revokeFamily(null);

        assertThat(stored(token).isRevoked()).isFalse();
    }

    @Test
    void revokingAllEndsEverySessionOfThatUserOnly() {
        IssuedRefreshToken phone = service.issue(asha, BROWSER);
        IssuedRefreshToken laptop = service.issue(asha, BROWSER);
        IssuedRefreshToken someoneElse = service.issue(users.save(user("ravi@example.com")), BROWSER);

        assertThat(service.revokeAll(asha)).isEqualTo(2);

        assertRejected(phone, ErrorCode.REFRESH_TOKEN_INVALID);
        assertRejected(laptop, ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(stored(someoneElse).isRevoked()).isFalse();
    }

    // --- Housekeeping --------------------------------------------------------------------------------

    @Test
    void purgeDeletesOnlyExpiredTokens() {
        IssuedRefreshToken old = service.issue(asha, BROWSER);
        // Rotated a day later, so the replacement outlives the token that points at it
        clock.advance(Duration.ofDays(1));
        IssuedRefreshToken replacement = service.rotate(old.value(), BROWSER).token();
        clock.advance(TTL.minusDays(1));

        assertThat(service.purgeExpired()).isEqualTo(1);

        entityManager.clear();
        assertThat(tokens.findAll()).extracting(RefreshToken::getTokenHash)
                .containsExactly(RefreshTokenService.hash(replacement.value()));
    }

    // -------------------------------------------------------------------------------------------------

    private void assertRejected(IssuedRefreshToken token, ErrorCode code) {
        assertThatThrownBy(() -> service.rotate(token.value(), BROWSER))
                .isInstanceOfSatisfying(InvalidRefreshTokenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private RefreshToken stored(IssuedRefreshToken token) {
        entityManager.flush();
        entityManager.clear();
        return tokens.findByTokenHash(RefreshTokenService.hash(token.value())).orElseThrow();
    }

    private static AppUser user(String email) {
        AppUser user = new AppUser(email, "Test User", Role.ADMIN);
        user.setPassword("{noop}x", START);
        return user;
    }
}
