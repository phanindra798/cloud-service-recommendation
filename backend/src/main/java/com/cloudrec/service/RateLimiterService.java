package com.cloudrec.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter: max N requests per window per user.
 * In-memory — sufficient for a single-instance deployment; swap for a
 * Redis-backed implementation when scaling horizontally.
 *
 * Users with no requests inside the window are evicted periodically so
 * memory stays bounded by the number of recently active users.
 */
@Service
public class RateLimiterService {

    static final int MAX_REQUESTS = 10;
    static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> requests = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiterService(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String key) {
        long now = clock.millis();
        while (true) {
            Deque<Long> timestamps = requests.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (timestamps) {
                // Evicted between lookup and lock — retry with a fresh deque.
                if (requests.get(key) != timestamps) continue;

                prune(timestamps, now);
                if (timestamps.size() >= MAX_REQUESTS) {
                    return false;
                }
                timestamps.addLast(now);
                return true;
            }
        }
    }

    /** Drops users whose whole window has expired. */
    @Scheduled(fixedDelay = WINDOW_MS)
    public void evictIdle() {
        long now = clock.millis();
        requests.forEach((key, timestamps) -> {
            synchronized (timestamps) {
                prune(timestamps, now);
                if (timestamps.isEmpty()) {
                    requests.remove(key, timestamps);
                }
            }
        });
    }

    int trackedUsers() {
        return requests.size();
    }

    private static void prune(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_MS) {
            timestamps.pollFirst();
        }
    }
}
