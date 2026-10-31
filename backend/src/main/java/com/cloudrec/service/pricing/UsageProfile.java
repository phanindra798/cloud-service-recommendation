package com.cloudrec.service.pricing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expected monthly usage of the application. Costs scale from these numbers
 * (see {@link Driver}), so a 50k-user app is not priced like a 10-user one.
 */
public record UsageProfile(long monthlyActiveUsers, long requestsPerMonth, long storageGb, long dataTransferGb) {

    /** Used when the AI is unavailable or returns no usage estimate. */
    public static final UsageProfile DEFAULT = new UsageProfile(1_000, 500_000, 20, 50);

    static final double SECONDS_PER_MONTH = 30 * 24 * 3600;
    /** Peak traffic is assumed to be this multiple of the monthly average. */
    static final double PEAK_TO_AVERAGE = 3.0;

    /** Clamps AI-supplied numbers into a sane range so one bad estimate can't produce absurd costs. */
    public UsageProfile clamped() {
        return new UsageProfile(
                clamp(monthlyActiveUsers, 1, 1_000_000_000L),
                clamp(requestsPerMonth, 1_000, 1_000_000_000_000L),
                clamp(storageGb, 1, 10_000_000L),
                clamp(dataTransferGb, 1, 10_000_000L));
    }

    public double peakRequestsPerSecond() {
        return requestsPerMonth / SECONDS_PER_MONTH * PEAK_TO_AVERAGE;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("monthlyActiveUsers", monthlyActiveUsers);
        map.put("requestsPerMonth", requestsPerMonth);
        map.put("storageGb", storageGb);
        map.put("dataTransferGb", dataTransferGb);
        map.put("peakRequestsPerSecond", Math.round(peakRequestsPerSecond() * 10) / 10.0);
        return map;
    }

    private static long clamp(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }
}
