package com.cloudrec.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

/**
 * Brute-force protection for the auth endpoints:
 *  - an email is locked after N failed logins within the lock window
 *    (applies to unknown emails too, so a lock reveals nothing)
 *  - each client IP gets a request budget per minute across login/register
 */
@Service
public class LoginAttemptService {

    private final SlidingWindow failures;
    private final SlidingWindow ipRequests;
    private final long lockMinutes;
    private final Counter failureCounter;
    private final Counter lockedCounter;
    private final Counter ipRejections;

    public LoginAttemptService(
            Clock clock,
            MeterRegistry meters,
            @Value("${security.login.max-failures:5}") int maxFailures,
            @Value("${security.login.lock-minutes:15}") long lockMinutes,
            @Value("${security.auth.max-requests-per-minute:20}") int maxRequestsPerMinute) {
        this.failures = new SlidingWindow(maxFailures, lockMinutes * 60_000, clock);
        this.ipRequests = new SlidingWindow(maxRequestsPerMinute, 60_000, clock);
        this.lockMinutes = lockMinutes;
        this.failureCounter = meters.counter("auth.login.failures");
        this.lockedCounter = meters.counter("auth.login.locked");
        this.ipRejections = meters.counter("auth.ratelimit.rejections");
    }

    public boolean allowRequestFrom(String ip) {
        if (ipRequests.tryAcquire(ip)) return true;
        ipRejections.increment();
        return false;
    }

    public boolean isLocked(String email) {
        boolean locked = failures.isFull(normalise(email));
        if (locked) lockedCounter.increment();
        return locked;
    }

    public void recordFailure(String email) {
        failures.record(normalise(email));
        failureCounter.increment();
    }

    public void recordSuccess(String email) {
        failures.clear(normalise(email));
    }

    public long lockMinutes() {
        return lockMinutes;
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictIdle() {
        failures.evictIdle();
        ipRequests.evictIdle();
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
