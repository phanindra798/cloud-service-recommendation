package com.cloudrec.service.pricing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combines what the user specified with the AI's estimate. Priority per field:
 * the user's own number, then the app-type profile (needs a user count), then
 * the AI estimate scaled to the user count, then the AI estimate as-is.
 */
public final class UsageResolver {

    public static final String FROM_USER = "you";
    public static final String FROM_APP_TYPE = "app type";
    public static final String FROM_AI = "AI estimate";

    private UsageResolver() {}

    public record Resolved(UsageProfile usage, Map<String, String> sources) {}

    public static Resolved resolve(Requirements req, UsageProfile ai) {
        Map<String, String> sources = new LinkedHashMap<>();

        long users = pick(req.monthlyActiveUsers(), ai.monthlyActiveUsers(), "monthlyActiveUsers", sources);
        // Keep the AI's per-user ratios when the user corrects only the user count
        double scale = (double) users / Math.max(1, ai.monthlyActiveUsers());

        long requests;
        long storage;
        long transfer;
        if (req.appType() != null) {
            requests = req.appType().requests(users);
            storage = req.appType().storageGb(users);
            transfer = req.appType().transferGb(users);
            sources.put("requestsPerMonth", FROM_APP_TYPE);
            sources.put("storageGb", FROM_APP_TYPE);
            sources.put("dataTransferGb", FROM_APP_TYPE);
        } else {
            requests = Math.round(ai.requestsPerMonth() * scale);
            storage = Math.round(ai.storageGb() * scale);
            transfer = Math.round(ai.dataTransferGb() * scale);
            sources.put("requestsPerMonth", FROM_AI);
            sources.put("storageGb", FROM_AI);
            sources.put("dataTransferGb", FROM_AI);
        }
        storage = pick(req.storageGb(), storage, "storageGb", sources);
        transfer = pick(req.dataTransferGb(), transfer, "dataTransferGb", sources);

        UsageProfile usage = new UsageProfile(users, requests, storage, transfer).clamped();
        return new Resolved(usage, sources);
    }

    private static long pick(Long fromUser, long fallback, String field, Map<String, String> sources) {
        if (fromUser != null) {
            sources.put(field, FROM_USER);
            return fromUser;
        }
        sources.putIfAbsent(field, FROM_AI);
        return fallback;
    }
}
