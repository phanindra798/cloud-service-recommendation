package com.cloudrec.service.pricing;

import java.util.ArrayList;
import java.util.List;

/**
 * What the user told us up front. Every field is optional; anything given
 * overrides the AI's estimate.
 */
public record Requirements(Region region, Long monthlyActiveUsers, AppType appType,
                           Long storageGb, Long dataTransferGb, boolean highAvailability) {

    public static final Requirements NONE = new Requirements(null, null, null, null, null, false);

    public Region regionOrDefault() {
        return region != null ? region : Region.US;
    }

    /** Plain-English constraints for the AI prompt; empty when nothing was given. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (region != null) parts.add("users are mainly in " + region.label());
        if (monthlyActiveUsers != null) parts.add("about " + monthlyActiveUsers + " monthly active users");
        if (appType != null) parts.add("the app is a " + appType.label());
        if (storageGb != null) parts.add("about " + storageGb + " GB of stored data");
        if (dataTransferGb != null) parts.add("about " + dataTransferGb + " GB/month served to users");
        if (highAvailability) parts.add("it must be highly available (no single point of failure)");
        return String.join("; ", parts);
    }
}
