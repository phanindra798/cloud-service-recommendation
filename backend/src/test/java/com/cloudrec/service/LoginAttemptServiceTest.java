package com.cloudrec.service;

import com.cloudrec.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final LoginAttemptService attempts = new LoginAttemptService(clock, meters, 5, 15, 20);

    @Test
    void locksAnEmailAfterFiveFailures() {
        for (int i = 0; i < 4; i++) attempts.recordFailure("alice@example.com");
        assertThat(attempts.isLocked("alice@example.com")).isFalse();

        attempts.recordFailure("alice@example.com");

        assertThat(attempts.isLocked("alice@example.com")).isTrue();
        assertThat(attempts.isLocked("bob@example.com")).isFalse();
        assertThat(meters.get("auth.login.failures").counter().count()).isEqualTo(5.0);
    }

    @Test
    void lockIsCaseInsensitive() {
        for (int i = 0; i < 5; i++) attempts.recordFailure("Alice@Example.com ");

        assertThat(attempts.isLocked("alice@example.com")).isTrue();
    }

    @Test
    void lockExpiresAfterTheWindow() {
        for (int i = 0; i < 5; i++) attempts.recordFailure("alice@example.com");

        clock.advance(Duration.ofMinutes(14));
        assertThat(attempts.isLocked("alice@example.com")).isTrue();

        clock.advance(Duration.ofMinutes(1));
        assertThat(attempts.isLocked("alice@example.com")).isFalse();
    }

    @Test
    void successfulLoginResetsFailures() {
        for (int i = 0; i < 4; i++) attempts.recordFailure("alice@example.com");
        attempts.recordSuccess("alice@example.com");
        attempts.recordFailure("alice@example.com");

        assertThat(attempts.isLocked("alice@example.com")).isFalse();
    }

    @Test
    void limitsRequestsPerIp() {
        for (int i = 0; i < 20; i++) {
            assertThat(attempts.allowRequestFrom("10.0.0.1")).isTrue();
        }
        assertThat(attempts.allowRequestFrom("10.0.0.1")).isFalse();
        assertThat(attempts.allowRequestFrom("10.0.0.2")).isTrue();
        assertThat(meters.get("auth.ratelimit.rejections").counter().count()).isEqualTo(1.0);

        clock.advance(Duration.ofMinutes(1));
        assertThat(attempts.allowRequestFrom("10.0.0.1")).isTrue();
    }

    @Test
    void evictionKeepsActiveLocks() {
        for (int i = 0; i < 5; i++) attempts.recordFailure("alice@example.com");
        attempts.evictIdle();
        assertThat(attempts.isLocked("alice@example.com")).isTrue();

        clock.advance(Duration.ofMinutes(16));
        attempts.evictIdle();
        assertThat(attempts.isLocked("alice@example.com")).isFalse();
    }
}
