package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.auth.RefreshTokenService.IssuedRefreshToken;
import com.example.fairnesstracker.common.exception.ErrorCode;
import com.example.fairnesstracker.user.AppUser;
import com.example.fairnesstracker.user.AppUserRepository;
import com.example.fairnesstracker.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real transactions on PostgreSQL: a revocation made while rejecting a token is committed, and concurrent
 * refreshes with one token are serialised by the row lock. No grace period, so any second use is a reuse.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "auth.refresh-token.reuse-grace=0s"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenConcurrencyTest {

    private static final ClientInfo BROWSER = new ClientInfo("203.0.113.7", "Firefox");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private RefreshTokenService service;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private AppUserRepository users;

    private AppUser asha;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser("asha@example.com", "Asha", Role.ADMIN);
        user.setPassword("{noop}x", Instant.now());
        asha = users.save(user);
    }

    @AfterEach
    void cleanUp() {
        tokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void theRevocationIsKeptAlthoughTheRequestFails() {
        IssuedRefreshToken stolen = service.issue(asha, BROWSER);
        IssuedRefreshToken current = service.rotate(stolen.value(), BROWSER).token();

        assertThatThrownBy(() -> service.rotate(stolen.value(), BROWSER))
                .isInstanceOfSatisfying(InvalidRefreshTokenException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REUSED));

        // Read outside any transaction (findByTokenHash locks, so it needs one)
        assertThat(tokens.findAll()).filteredOn(t -> t.getTokenHash().equals(RefreshTokenService.hash(current.value())))
                .singleElement().matches(RefreshToken::isRevoked);
    }

    @Test
    void concurrentRefreshesWithOneTokenRotateItOnlyOnce() throws Exception {
        IssuedRefreshToken token = service.issue(asha, BROWSER);
        int callers = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> refreshes = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            refreshes.add(() -> {
                start.await();
                try {
                    service.rotate(token.value(), BROWSER);
                    return true;
                } catch (InvalidRefreshTokenException e) {
                    return false;
                }
            });
        }

        List<Boolean> succeeded = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            List<Future<Boolean>> results = refreshes.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Boolean> result : results) {
                succeeded.add(result.get());
            }
        }

        assertThat(succeeded).containsOnlyOnce(true);
        // The second caller saw a replaced token and ended the session, so nothing in the family is usable
        assertThat(tokens.findAll()).allMatch(RefreshToken::isRevoked);
    }
}
