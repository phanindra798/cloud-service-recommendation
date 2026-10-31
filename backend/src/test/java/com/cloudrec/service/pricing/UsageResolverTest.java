package com.cloudrec.service.pricing;

import org.junit.jupiter.api.Test;

import static com.cloudrec.service.pricing.UsageResolver.FROM_AI;
import static com.cloudrec.service.pricing.UsageResolver.FROM_APP_TYPE;
import static com.cloudrec.service.pricing.UsageResolver.FROM_USER;
import static org.assertj.core.api.Assertions.assertThat;

class UsageResolverTest {

    private static final UsageProfile AI = new UsageProfile(10_000, 3_000_000, 100, 500);

    @Test
    void noRequirementsKeepsTheAiEstimate() {
        UsageResolver.Resolved r = UsageResolver.resolve(Requirements.NONE, AI);

        assertThat(r.usage()).isEqualTo(AI);
        assertThat(r.sources()).containsOnly(
                java.util.Map.entry("monthlyActiveUsers", FROM_AI),
                java.util.Map.entry("requestsPerMonth", FROM_AI),
                java.util.Map.entry("storageGb", FROM_AI),
                java.util.Map.entry("dataTransferGb", FROM_AI));
    }

    @Test
    void userCountScalesTheAiPerUserRatios() {
        Requirements req = new Requirements(null, 50_000L, null, null, null, false);

        UsageResolver.Resolved r = UsageResolver.resolve(req, AI);

        assertThat(r.usage()).isEqualTo(new UsageProfile(50_000, 15_000_000, 500, 2_500));
        assertThat(r.sources()).containsEntry("monthlyActiveUsers", FROM_USER).containsEntry("requestsPerMonth", FROM_AI);
    }

    @Test
    void appTypeDerivesTrafficFromUsers() {
        Requirements req = new Requirements(null, 100_000L, AppType.VIDEO_STREAMING, null, null, false);

        UsageResolver.Resolved r = UsageResolver.resolve(req, AI);

        // 500 req, 20 MB stored and 8,000 MB served per user per month
        assertThat(r.usage()).isEqualTo(new UsageProfile(100_000, 50_000_000, 10 + 1_953, 781_250));
        assertThat(r.sources()).containsEntry("requestsPerMonth", FROM_APP_TYPE)
                .containsEntry("dataTransferGb", FROM_APP_TYPE);
    }

    @Test
    void appTypeWithoutUserCountUsesTheAiUserCount() {
        Requirements req = new Requirements(null, null, AppType.BLOG, null, null, false);

        UsageResolver.Resolved r = UsageResolver.resolve(req, AI);

        assertThat(r.usage().requestsPerMonth()).isEqualTo(500_000);
        assertThat(r.sources()).containsEntry("monthlyActiveUsers", FROM_AI);
    }

    @Test
    void explicitStorageAndTransferWinOverEverything() {
        Requirements req = new Requirements(null, 100_000L, AppType.SOCIAL, 42L, 7L, false);

        UsageResolver.Resolved r = UsageResolver.resolve(req, AI);

        assertThat(r.usage().storageGb()).isEqualTo(42);
        assertThat(r.usage().dataTransferGb()).isEqualTo(7);
        assertThat(r.sources()).containsEntry("storageGb", FROM_USER).containsEntry("dataTransferGb", FROM_USER)
                .containsEntry("requestsPerMonth", FROM_APP_TYPE);
    }

    @Test
    void describesRequirementsForThePrompt() {
        Requirements req = new Requirements(Region.INDIA, 50_000L, AppType.ECOMMERCE, null, null, true);

        assertThat(req.describe()).isEqualTo("users are mainly in India; about 50000 monthly active users; "
                + "the app is a E-commerce store; it must be highly available (no single point of failure)");
        assertThat(Requirements.NONE.describe()).isEmpty();
        assertThat(Requirements.NONE.regionOrDefault()).isEqualTo(Region.US);
    }
}
