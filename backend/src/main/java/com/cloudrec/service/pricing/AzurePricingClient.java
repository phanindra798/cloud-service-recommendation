package com.cloudrec.service.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live prices from the Azure Retail Prices API (public, no key required).
 * https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices
 *
 * Strategy: one representative meter per service, converted to a unit price
 * that matches the service's {@link Driver} in {@link ServiceCatalog}
 * (USD per instance-month, or per GB). Only services whose Azure meter maps
 * cleanly onto that unit are looked up live. Results are cached for 24 h; any
 * failure falls back to the catalog's list-price estimate.
 */
@Component
public class AzurePricingClient {

    private static final Logger log = LoggerFactory.getLogger(AzurePricingClient.class);

    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final double HOURS_PER_MONTH = 730.0;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, CachedPrice> cache = new ConcurrentHashMap<>();
    private final String region;
    private final String apiUrl;
    private final boolean warmCacheOnStartup;

    /** Azure service name → OData filter (region appended at query time). */
    private final Map<String, String> filters;

    public AzurePricingClient(
            @Value("${azure.pricing.region:centralindia}") String region,
            @Value("${azure.pricing.api-url:https://prices.azure.com/api/retail/prices}") String apiUrl,
            @Value("${azure.pricing.warm-cache:true}") boolean warmCacheOnStartup) {
        this.region = region;
        this.apiUrl = apiUrl;
        this.warmCacheOnStartup = warmCacheOnStartup;
        this.filters = Map.of(
                "Virtual Machines",
                "serviceName eq 'Virtual Machines' and armSkuName eq 'Standard_B2s' and priceType eq 'Consumption'",
                "Blob Storage",
                "serviceName eq 'Storage' and meterName eq 'Hot LRS Data Stored' and priceType eq 'Consumption'",
                "SQL Database",
                "serviceName eq 'SQL Database' and skuName eq 'S0' and priceType eq 'Consumption'",
                "CDN",
                "serviceName eq 'Content Delivery Network' and meterName eq 'Standard Data Transfer' and priceType eq 'Consumption'"
        );
    }

    private record CachedPrice(double unitPrice, long fetchedAt) {}

    /** Warm the cache in the background at startup so first user request is fast. */
    @EventListener(ContextRefreshedEvent.class)
    public void warmCache() {
        if (!warmCacheOnStartup) return;
        CompletableFuture.runAsync(() -> filters.keySet().forEach(this::getUnitPrice));
    }

    /** Whether this service has a live price lookup at all. */
    public boolean supports(String azureService) {
        return filters.containsKey(azureService);
    }

    /** USD per unit (instance-month or GB) for the given Azure service, empty if no live price. */
    public Optional<Double> getUnitPrice(String azureService) {
        String filter = filters.get(azureService);
        if (filter == null) return Optional.empty();

        CachedPrice cached = cache.get(azureService);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < CACHE_TTL_MS) {
            return Optional.of(cached.unitPrice());
        }

        try {
            String fullFilter = filter + " and armRegionName eq '" + region + "'";
            String url = apiUrl + "?api-version=2023-01-01-preview&$filter=" + URLEncoder.encode(fullFilter, StandardCharsets.UTF_8).replace("+", "%20");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();

            JsonNode itemsNode = mapper.readTree(response.body()).path("Items");

            for (JsonNode item : itemsNode) {
                // Skip Windows-licensed SKUs so compute estimates reflect Linux pricing
                if (item.path("productName").asText().contains("Windows")) continue;

                double price = item.path("retailPrice").asDouble(0);
                if (price <= 0) continue;

                Double unitPrice = toUnitPrice(price, item.path("unitOfMeasure").asText(""));
                if (unitPrice != null) {
                    double rounded = Math.round(unitPrice * 10_000.0) / 10_000.0;
                    cache.put(azureService, new CachedPrice(rounded, System.currentTimeMillis()));
                    return Optional.of(rounded);
                }
            }
        } catch (Exception e) {
            log.warn("Azure pricing lookup failed for {}: {}", azureService, e.toString());
        }
        return Optional.empty();
    }

    /** Convert a retail price to USD per instance-month or per GB, based on its unit of measure. */
    static Double toUnitPrice(double price, String unit) {
        String u = unit.toLowerCase();
        if (u.contains("hour")) return price * HOURS_PER_MONTH;
        if (u.contains("/day") || u.contains("1 day")) return price * 30;
        if (u.contains("/month")) return price;
        if (u.contains("gb")) return price;
        return null;
    }
}
