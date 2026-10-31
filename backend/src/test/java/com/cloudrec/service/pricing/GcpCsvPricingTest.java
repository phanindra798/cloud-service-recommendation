package com.cloudrec.service.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GcpCsvPricingTest {

    @Test
    void loadsRowsAndSkipsHeaderAndMalformedLines() {
        GcpCsvPricing pricing = new GcpCsvPricing();
        pricing.load(csv("""
                service,monthlyUsd
                Compute Engine, 6.85
                Cloud Storage,2.30
                broken line
                Cloud SQL,not-a-number
                """));

        assertThat(pricing.isLoaded()).isTrue();
        assertThat(pricing.getUnitPrice("Compute Engine")).contains(6.85);
        assertThat(pricing.getUnitPrice("Cloud Storage")).contains(2.30);
        assertThat(pricing.getUnitPrice("Cloud SQL")).isEmpty();
    }

    @Test
    void notLoadedWhenFileMissing() {
        GcpCsvPricing pricing = new GcpCsvPricing();
        pricing.load(new ClassPathResource("pricing/does-not-exist.csv"));

        assertThat(pricing.isLoaded()).isFalse();
        assertThat(pricing.getUnitPrice("Compute Engine")).isEmpty();
    }

    private ByteArrayResource csv(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public boolean exists() {
                return true;
            }
        };
    }
}
