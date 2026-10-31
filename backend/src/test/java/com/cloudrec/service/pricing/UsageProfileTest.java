package com.cloudrec.service.pricing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UsageProfileTest {

    @Test
    void peakIsThreeTimesTheMonthlyAverage() {
        UsageProfile usage = new UsageProfile(1, 2_592_000, 1, 1); // 1 req/s on average

        assertThat(usage.peakRequestsPerSecond()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void clampsOutOfRangeEstimates() {
        UsageProfile clamped = new UsageProfile(0, -5, 0, Long.MAX_VALUE).clamped();

        assertThat(clamped).isEqualTo(new UsageProfile(1, 1_000, 1, 10_000_000L));
    }

    @Test
    void leavesSaneValuesAlone() {
        UsageProfile usage = new UsageProfile(50_000, 30_000_000, 500, 2_000);

        assertThat(usage.clamped()).isEqualTo(usage);
    }

    @Test
    void driversSizeInstancesFromPeakLoad() {
        UsageProfile quiet = new UsageProfile(100, 10_000, 5, 5);
        UsageProfile busy = new UsageProfile(1_000_000, 1_000_000_000, 5_000, 50_000); // ~1157 peak req/s

        assertThat(Driver.COMPUTE_INSTANCE.quantity(quiet)).isEqualTo(1);
        assertThat(Driver.COMPUTE_INSTANCE.quantity(busy)).isEqualTo(24);
        assertThat(Driver.DATABASE_INSTANCE.quantity(busy)).isEqualTo(3);
        assertThat(Driver.CACHE_NODE.quantity(busy)).isEqualTo(1);
        assertThat(Driver.MILLION_REQUESTS.quantity(busy)).isEqualTo(1_000);
        assertThat(Driver.THOUSAND_USERS.quantity(busy)).isEqualTo(1_000);
        assertThat(Driver.STORAGE_GB.quantity(busy)).isEqualTo(5_000);
        assertThat(Driver.TRANSFER_GB.quantity(busy)).isEqualTo(50_000);
    }

    @Test
    void highAvailabilityRules() {
        UsageProfile quiet = new UsageProfile(100, 10_000, 5, 5);
        UsageProfile busy = new UsageProfile(1_000_000, 1_000_000_000, 5_000, 50_000);

        assertThat(Driver.COMPUTE_INSTANCE.quantity(quiet, true)).isEqualTo(2);  // at least two
        assertThat(Driver.COMPUTE_INSTANCE.quantity(busy, true)).isEqualTo(24);  // already more than two
        assertThat(Driver.DATABASE_INSTANCE.quantity(busy, true)).isEqualTo(6);  // standby per primary
        assertThat(Driver.CACHE_NODE.quantity(quiet, true)).isEqualTo(2);
        assertThat(Driver.STORAGE_GB.quantity(quiet, true)).isEqualTo(5);        // managed, unchanged
        assertThat(Driver.MILLION_REQUESTS.quantity(busy, true)).isEqualTo(1_000);
    }
}
