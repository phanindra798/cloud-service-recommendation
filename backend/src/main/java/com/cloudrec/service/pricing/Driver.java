package com.cloudrec.service.pricing;

import java.util.function.ToDoubleFunction;

/**
 * What a service is billed by, and how many of those units a usage profile
 * needs. Instance counts are sized from peak requests per second.
 */
public enum Driver {

    /** App servers / containers; one handles ~50 req/s at peak. */
    COMPUTE_INSTANCE("instance", u -> instances(u, 50)),
    /** Managed relational database instances; one handles ~500 queries/s. */
    DATABASE_INSTANCE("instance", u -> instances(u, 500)),
    /** In-memory cache nodes; one handles ~5,000 ops/s. */
    CACHE_NODE("node", u -> instances(u, 5_000)),
    STORAGE_GB("GB", u -> u.storageGb()),
    TRANSFER_GB("GB transferred", u -> u.dataTransferGb()),
    MILLION_REQUESTS("million requests", u -> u.requestsPerMonth() / 1_000_000.0),
    THOUSAND_USERS("thousand MAU", u -> u.monthlyActiveUsers() / 1_000.0);

    private final String unit;
    private final ToDoubleFunction<UsageProfile> quantity;

    Driver(String unit, ToDoubleFunction<UsageProfile> quantity) {
        this.unit = unit;
        this.quantity = quantity;
    }

    public String unit() {
        return unit;
    }

    public double quantity(UsageProfile usage) {
        return quantity.applyAsDouble(usage);
    }

    private static double instances(UsageProfile u, double capacityPerSecond) {
        return Math.max(1, Math.ceil(u.peakRequestsPerSecond() / capacityPerSecond));
    }
}
