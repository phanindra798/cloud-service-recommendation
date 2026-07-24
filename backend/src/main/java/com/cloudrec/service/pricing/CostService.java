package com.cloudrec.service.pricing;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps recommended AWS services to Azure/GCP equivalents and computes an
 * estimated monthly cost per provider.
 *
 * Pricing sources:
 *  - AWS:   static baseline (AWS Price List Query API requires SigV4-signed
 *           requests, i.e. AWS credentials — plug point in place)
 *  - Azure: live Azure Retail Prices API (public), cached 24 h, baseline fallback
 *  - GCP:   local CSV export when provided, otherwise static baseline
 */
@Service
public class CostService {

    private final AzurePricingClient azurePricing;
    private final GcpCsvPricing gcpPricing;

    public CostService(AzurePricingClient azurePricing, GcpCsvPricing gcpPricing) {
        this.azurePricing = azurePricing;
        this.gcpPricing = gcpPricing;
    }

    public Map<String, Object> process(List<String> awsServices) {

        List<String> azure = new ArrayList<>();
        List<String> gcp = new ArrayList<>();

        for (String service : awsServices) {
            Map<String, String> mapping = ServiceCatalog.SERVICE_MAPPING.get(service);
            if (mapping != null) {
                azure.add(mapping.get("azure"));
                gcp.add(mapping.get("gcp"));
            }
        }

        boolean[] azureLiveUsed = {false};
        boolean[] gcpCsvUsed = {false};

        double awsTotal = round(awsServices.stream()
                .mapToDouble(s -> ServiceCatalog.AWS_BASELINE.getOrDefault(s, 0.0))
                .sum());

        double azureTotal = round(azure.stream()
                .mapToDouble(s -> azurePricing.getMonthlyPrice(s)
                        .map(p -> { azureLiveUsed[0] = true; return p; })
                        .orElse(ServiceCatalog.AZURE_BASELINE.getOrDefault(s, 0.0)))
                .sum());

        double gcpTotal = round(gcp.stream()
                .mapToDouble(s -> gcpPricing.getMonthlyPrice(s)
                        .map(p -> { gcpCsvUsed[0] = true; return p; })
                        .orElse(ServiceCatalog.GCP_BASELINE.getOrDefault(s, 0.0)))
                .sum());

        String best = "AWS";
        double min = awsTotal;
        if (azureTotal < min) { min = azureTotal; best = "Azure"; }
        if (gcpTotal < min) { best = "GCP"; }

        Map<String, Object> result = new HashMap<>();
        result.put("aws", awsServices);
        result.put("azure", azure);
        result.put("gcp", gcp);
        result.put("cost", Map.of("aws", awsTotal, "azure", azureTotal, "gcp", gcpTotal));
        result.put("best", best);
        result.put("pricingSource", Map.of(
                "aws", "static baseline",
                "azure", azureLiveUsed[0] ? "live (Azure Retail Prices API)" : "static baseline",
                "gcp", gcpCsvUsed[0] ? "csv export" : "static baseline"
        ));
        return result;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
