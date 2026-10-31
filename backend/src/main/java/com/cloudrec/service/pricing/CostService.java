package com.cloudrec.service.pricing;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Maps recommended AWS services to Azure/GCP equivalents and estimates the
 * monthly cost of each one from the usage profile:
 *
 *   cost = fixedMonthly + unitPrice × quantity(usage, highAvailability)
 *
 * List-price estimates are scaled by the region's price multiplier; live and
 * CSV prices are used as-is.
 *
 * Unit price sources:
 *  - AWS:   list-price estimate (AWS Price List Query API requires SigV4-signed
 *           requests, i.e. AWS credentials — plug point in place)
 *  - Azure: live Azure Retail Prices API where a matching meter exists,
 *           cached 24 h, otherwise list-price estimate
 *  - GCP:   local CSV export when provided, otherwise list-price estimate
 */
@Service
public class CostService {

    static final String ESTIMATE = "list-price estimate";

    private final AzurePricingClient azurePricing;
    private final GcpCsvPricing gcpPricing;
    private final MeterRegistry meters;

    public CostService(AzurePricingClient azurePricing, GcpCsvPricing gcpPricing, MeterRegistry meters) {
        this.azurePricing = azurePricing;
        this.gcpPricing = gcpPricing;
        this.meters = meters;
    }

    public Map<String, Object> process(List<String> awsServices, UsageProfile usage) {
        return process(awsServices, usage, Region.US, false);
    }

    public Map<String, Object> process(List<String> awsServices, UsageProfile usage,
                                       Region region, boolean highAvailability) {
        Sizing sizing = new Sizing(usage, region, highAvailability);

        List<String> azure = new ArrayList<>();
        List<String> gcp = new ArrayList<>();
        for (String service : awsServices) {
            Map<String, String> mapping = ServiceCatalog.SERVICE_MAPPING.get(service);
            if (mapping != null) {
                azure.add(mapping.get("azure"));
                gcp.add(mapping.get("gcp"));
            }
        }

        List<Map<String, Object>> awsItems = price(awsServices, ServiceCatalog.AWS_PRICES, sizing,
                s -> Optional.empty(), null);
        List<Map<String, Object>> azureItems = price(azure, ServiceCatalog.AZURE_PRICES, sizing,
                s -> liveAzurePrice(s, region), "live");
        List<Map<String, Object>> gcpItems = price(gcp, ServiceCatalog.GCP_PRICES, sizing,
                gcpPricing::getUnitPrice, "csv");

        double awsTotal = total(awsItems);
        double azureTotal = total(azureItems);
        double gcpTotal = total(gcpItems);

        String best = "AWS";
        double min = awsTotal;
        if (azureTotal < min) { min = azureTotal; best = "Azure"; }
        if (gcpTotal < min) { best = "GCP"; }

        Map<String, Object> result = new HashMap<>();
        result.put("aws", awsServices);
        result.put("azure", azure);
        result.put("gcp", gcp);
        result.put("cost", Map.of("aws", awsTotal, "azure", azureTotal, "gcp", gcpTotal));
        result.put("breakdown", Map.of("aws", awsItems, "azure", azureItems, "gcp", gcpItems));
        result.put("usage", usage.toMap());
        result.put("region", region.toMap());
        result.put("highAvailability", highAvailability);
        result.put("best", best);
        result.put("pricingSource", Map.of(
                "aws", ESTIMATE,
                "azure", anySource(azureItems, "live") ? "live (Azure Retail Prices API)" : ESTIMATE,
                "gcp", anySource(gcpItems, "csv") ? "csv export" : ESTIMATE
        ));
        return result;
    }

    private record Sizing(UsageProfile usage, Region region, boolean highAvailability) {}

    private Optional<Double> liveAzurePrice(String service, Region region) {
        if (!azurePricing.supports(service)) return Optional.empty();
        Optional<Double> price = azurePricing.getUnitPrice(service, region.azure());
        if (price.isEmpty()) {
            meters.counter("pricing.azure.fallbacks", "service", service).increment();
        }
        return price;
    }

    /** One line item per known service; unknown services are skipped. */
    private List<Map<String, Object>> price(List<String> services, Map<String, PriceRule> catalog,
                                            Sizing sizing, Function<String, Optional<Double>> live,
                                            String liveSource) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String service : services) {
            PriceRule rule = catalog.get(service);
            if (rule == null) continue;

            Optional<Double> livePrice = live.apply(service);
            double unitPrice = livePrice.orElse(round(rule.unitPrice() * sizing.region().priceMultiplier(), 4));
            double quantity = rule.driver().quantity(sizing.usage(), sizing.highAvailability());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("service", service);
            item.put("quantity", round(quantity));
            item.put("unit", rule.driver().unit());
            item.put("unitPrice", unitPrice);
            item.put("monthlyCost", round(rule.fixedMonthly() + unitPrice * quantity));
            item.put("source", livePrice.isPresent() ? liveSource : ESTIMATE);
            items.add(item);
        }
        return items;
    }

    private static double total(List<Map<String, Object>> items) {
        return round(items.stream().mapToDouble(i -> (double) i.get("monthlyCost")).sum());
    }

    private static boolean anySource(List<Map<String, Object>> items, String source) {
        return items.stream().anyMatch(i -> source.equals(i.get("source")));
    }

    private static double round(double v) {
        return round(v, 2);
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
