package com.cloudrec.service;

import com.cloudrec.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RateLimiterService limiter = new RateLimiterService(clock);

    @Test
    void allowsUpToTenRequestsThenBlocks() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.allow("alice@example.com")).as("request %d", i + 1).isTrue();
        }
        assertThat(limiter.allow("alice@example.com")).isFalse();
    }

    @Test
    void limitsAreTrackedPerUser() {
        for (int i = 0; i < 10; i++) {
            limiter.allow("alice@example.com");
        }
        assertThat(limiter.allow("alice@example.com")).isFalse();
        assertThat(limiter.allow("bob@example.com")).isTrue();
    }

    @Test
    void windowSlidesAsOldRequestsExpire() {
        for (int i = 0; i < 10; i++) {
            limiter.allow("alice@example.com");
            clock.advance(Duration.ofSeconds(5));
        }
        // t=50s: oldest request (t=0) is still inside the 60 s window
        assertThat(limiter.allow("alice@example.com")).isFalse();

        clock.advance(Duration.ofSeconds(10));
        // t=60s: the t=0 request has expired, freeing exactly one slot
        assertThat(limiter.allow("alice@example.com")).isTrue();
        assertThat(limiter.allow("alice@example.com")).isFalse();
    }

    @Test
    void evictsUsersIdleForAWholeWindow() {
        limiter.allow("alice@example.com");
        clock.advance(Duration.ofSeconds(30));
        limiter.allow("bob@example.com");

        clock.advance(Duration.ofSeconds(30));
        limiter.evictIdle();
        assertThat(limiter.trackedUsers()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(30));
        limiter.evictIdle();
        assertThat(limiter.trackedUsers()).isZero();
    }

    @Test
    void evictionDoesNotResetActiveUsers() {
        for (int i = 0; i < 10; i++) {
            limiter.allow("alice@example.com");
        }
        limiter.evictIdle();

        assertThat(limiter.allow("alice@example.com")).isFalse();
    }

    @Test
    void neverAllowsMoreThanTheLimitUnderConcurrency() throws InterruptedException {
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                if (limiter.allow("carol@example.com")) allowed.incrementAndGet();
                limiter.evictIdle();
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(10);
    }
}
