package com.cloudrec.service.pricing;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
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
 * Format (no header): gcpServiceName,monthlyUsd
 *   Compute Engine,6.85
 *   Cloud Storage,2.30
 *
 * When the file is absent, callers fall back to the static baseline.
 */
@Component
public class GcpCsvPricing {

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private boolean loaded = false;

    @PostConstruct
    public void load() {
        try {
            ClassPathResource resource = new ClassPathResource("pricing/gcp-prices.csv");
            if (!resource.exists()) {
                System.out.println("GCP pricing CSV not found — using static baseline prices for GCP.");
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
            System.out.println("Loaded " + prices.size() + " GCP prices from CSV.");
        } catch (Exception e) {
            System.out.println("Failed to load GCP pricing CSV: " + e.getMessage());
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Optional<Double> getMonthlyPrice(String gcpService) {
        return Optional.ofNullable(prices.get(gcpService));
    }
}
