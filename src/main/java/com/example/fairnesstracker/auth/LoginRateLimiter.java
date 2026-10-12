package com.example.fairnesstracker.auth;

import com.example.fairnesstracker.common.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sign-in attempts per client IP: a token bucket that refills to {@code attemptsPerIpPerMinute} each minute.
 * Checked before any database or BCrypt work, so a flood costs almost nothing. Buckets live in this
 * instance's memory (bounded; idle ones expire), so with several instances the limit applies per instance.
 */
@Slf4j
@Component
class LoginRateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private final int attemptsPerMinute;

    LoginRateLimiter(LoginProperties properties) {
        this.attemptsPerMinute = properties.attemptsPerIpPerMinute();
    }

    void check(String clientIp) {
        Bucket bucket = buckets.get(clientIp == null ? "unknown" : clientIp, ip -> Bucket.builder()
                .addLimit(limit -> limit.capacity(attemptsPerMinute).refillGreedy(attemptsPerMinute, Duration.ofMinutes(1)))
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            log.warn("Sign-in rate limit reached for {}", clientIp);
            throw new TooManyRequestsException("Too many sign-in attempts; wait a moment and try again",
                    Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }
}
