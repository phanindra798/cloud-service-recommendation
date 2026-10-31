package com.cloudrec.service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe per-key sliding-window counter: events older than the window
 * are forgotten. In-memory — fine for a single instance; swap for Redis when
 * scaling horizontally. Keys whose window is empty can be evicted so memory
 * stays bounded by recently active keys.
 */
public class SlidingWindow {

    private final int limit;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

    public SlidingWindow(int limit, long windowMs, Clock clock) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    /** Records an event if the key is under the limit; returns whether it was allowed. */
    public boolean tryAcquire(String key) {
        return withDeque(key, (timestamps, now) -> {
            if (timestamps.size() >= limit) return false;
            timestamps.addLast(now);
            return true;
        });
    }

    /** Records an event unconditionally (e.g. a failed login). */
    public void record(String key) {
        withDeque(key, (timestamps, now) -> timestamps.add(now));
    }

    /** Whether the key has reached the limit within the window. */
    public boolean isFull(String key) {
        Deque<Long> timestamps = events.get(key);
        if (timestamps == null) return false;
        synchronized (timestamps) {
            prune(timestamps, clock.millis());
            return timestamps.size() >= limit;
        }
    }

    public void clear(String key) {
        events.remove(key);
    }

    /** Drops keys whose whole window has expired. */
    public void evictIdle() {
        long now = clock.millis();
        events.forEach((key, timestamps) -> {
            synchronized (timestamps) {
                prune(timestamps, now);
                if (timestamps.isEmpty()) {
                    events.remove(key, timestamps);
                }
            }
        });
    }

    public Map<String, ?> view() {
        return events;
    }

    private interface Action {
        boolean apply(Deque<Long> timestamps, long now);
    }

    private boolean withDeque(String key, Action action) {
        long now = clock.millis();
        while (true) {
            Deque<Long> timestamps = events.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (timestamps) {
                // Evicted between lookup and lock — retry with a fresh deque.
                if (events.get(key) != timestamps) continue;
                prune(timestamps, now);
                return action.apply(timestamps, now);
            }
        }
    }

    private void prune(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMs) {
            timestamps.pollFirst();
        }
    }
}
