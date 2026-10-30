package com.cloudrec.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private final RateLimiterService limiter = new RateLimiterService();

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
    void neverAllowsMoreThanTheLimitUnderConcurrency() throws InterruptedException {
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                if (limiter.allow("carol@example.com")) allowed.incrementAndGet();
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(10);
    }
}
