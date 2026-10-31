package com.cloudrec.service.pricing;

import java.util.function.ToDoubleFunction;

/**
 * What a service is billed by, and how many of those units a usage profile
 * needs. Instance counts are sized from peak requests per second.
 */
public enum Driver {

    /** App servers / containers; one handles ~50 req/s at peak. HA: at least two. */
    COMPUTE_INSTANCE("instance", u -> instances(u, 50), HighAvailability.AT_LEAST_TWO),
    /** Managed relational database instances; one handles ~500 queries/s. HA: a standby per primary. */
    DATABASE_INSTANCE("instance", u -> instances(u, 500), HighAvailability.STANDBY),
    /** In-memory cache nodes; one handles ~5,000 ops/s. HA: a replica per node. */
    CACHE_NODE("node", u -> instances(u, 5_000), HighAvailability.STANDBY),
    STORAGE_GB("GB", u -> u.storageGb(), HighAvailability.NONE),
    TRANSFER_GB("GB transferred", u -> u.dataTransferGb(), HighAvailability.NONE),
    MILLION_REQUESTS("million requests", u -> u.requestsPerMonth() / 1_000_000.0, HighAvailability.NONE),
    THOUSAND_USERS("thousand MAU", u -> u.monthlyActiveUsers() / 1_000.0, HighAvailability.NONE);

    /** How high availability changes the quantity. Managed/serverless services are already redundant. */
    private enum HighAvailability { NONE, AT_LEAST_TWO, STANDBY }

    private final String unit;
    private final ToDoubleFunction<UsageProfile> quantity;
    private final HighAvailability ha;

    Driver(String unit, ToDoubleFunction<UsageProfile> quantity, HighAvailability ha) {
        this.unit = unit;
        this.quantity = quantity;
        this.ha = ha;
    }

    public String unit() {
        return unit;
    }

    public double quantity(UsageProfile usage) {
        return quantity(usage, false);
    }

    public double quantity(UsageProfile usage, boolean highAvailability) {
        double q = quantity.applyAsDouble(usage);
        if (!highAvailability) return q;
        return switch (ha) {
            case NONE -> q;
            case AT_LEAST_TWO -> Math.max(2, q);
            case STANDBY -> q * 2;
        };
    }

    private static double instances(UsageProfile u, double capacityPerSecond) {
        return Math.max(1, Math.ceil(u.peakRequestsPerSecond() / capacityPerSecond));
    }
}
