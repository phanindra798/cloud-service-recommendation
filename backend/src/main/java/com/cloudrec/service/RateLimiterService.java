package com.cloudrec.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Sliding-window rate limiter for recommendations: max N requests per window
 * per user, protecting the AI quota. Idle users are evicted periodically.
 */
@Service
public class RateLimiterService {

    static final int MAX_REQUESTS = 10;
    static final long WINDOW_MS = 60_000;

    private final SlidingWindow window;
    private final Counter rejections;

    public RateLimiterService(Clock clock, MeterRegistry meters) {
        this.window = new SlidingWindow(MAX_REQUESTS, WINDOW_MS, clock);
        this.rejections = meters.counter("ratelimit.rejections");
        meters.gaugeMapSize("ratelimit.tracked.users", Tags.empty(), window.view());
    }

    public boolean allow(String key) {
        if (window.tryAcquire(key)) return true;
        rejections.increment();
        return false;
    }

    /** Drops users whose whole window has expired. */
    @Scheduled(fixedDelay = WINDOW_MS)
    public void evictIdle() {
        window.evictIdle();
    }

    int trackedUsers() {
        return window.view().size();
    }
}
