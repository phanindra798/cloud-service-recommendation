package com.cloudrec.service.pricing;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional GCP pricing from a local CSV export (GCP has no public keyless
 * pricing API; Google offers pricing exports/CSV downloads instead).
 *
 * Expected file: src/main/resources/pricing/gcp-prices.csv
 * Format (no header): gcpServiceName,unitPriceUsd — the price per unit of the
 * service's driver in ServiceCatalog (per instance-month, per GB, per million
 * requests, ...):
 *   Compute Engine,24.46
 *   Cloud Storage,0.020
 *
 * When the file is absent, callers fall back to the static baseline.
 */
@Component
public class GcpCsvPricing {

    private static final Logger log = LoggerFactory.getLogger(GcpCsvPricing.class);

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private boolean loaded = false;

    @PostConstruct
    public void load() {
        load(new ClassPathResource("pricing/gcp-prices.csv"));
    }

    void load(Resource resource) {
        try {
            if (!resource.exists()) {
                log.info("GCP pricing CSV not found — using static baseline prices for GCP");
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length < 2) continue;
                    try {
                        prices.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                    } catch (NumberFormatException ignored) {
                        // header or malformed row — skip
                    }
                }
            }
            loaded = !prices.isEmpty();
            log.info("Loaded {} GCP prices from CSV", prices.size());
        } catch (Exception e) {
            log.warn("Failed to load GCP pricing CSV", e);
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Optional<Double> getUnitPrice(String gcpService) {
        return Optional.ofNullable(prices.get(gcpService));
    }
}
