package com.cloudrec.service.pricing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostServiceTest {

    @Mock
    private AzurePricingClient azure;
    @Mock
    private GcpCsvPricing gcp;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void mapsServicesToEquivalentsOnEveryProvider() {
        noLivePrices();

        Map<String, Object> result = new CostService(azure, gcp, meters).process(List.of("EC2", "S3", "DynamoDB"));

        assertThat(result.get("aws")).isEqualTo(List.of("EC2", "S3", "DynamoDB"));
        assertThat(result.get("azure")).isEqualTo(List.of("Virtual Machines", "Blob Storage", "Cosmos DB"));
        assertThat(result.get("gcp")).isEqualTo(List.of("Compute Engine", "Cloud Storage", "Firestore"));
    }

    @Test
    void usesBaselinesWhenNoLivePricing() {
        noLivePrices();

        Map<String, Object> result = new CostService(azure, gcp, meters).process(List.of("EC2", "RDS"));

        assertThat(cost(result)).containsEntry("aws", 23.0).containsEntry("azure", 25.0).containsEntry("gcp", 21.0);
        assertThat(result.get("best")).isEqualTo("GCP");
        assertThat(sources(result)).containsEntry("aws", "static baseline")
                .containsEntry("azure", "static baseline")
                .containsEntry("gcp", "static baseline");
    }

    @Test
    void prefersLiveAzureAndCsvGcpPricesAndReportsSource() {
        when(azure.getMonthlyPrice("Virtual Machines")).thenReturn(Optional.of(1.25));
        when(azure.getMonthlyPrice("SQL Database")).thenReturn(Optional.empty());
        when(gcp.getMonthlyPrice("Compute Engine")).thenReturn(Optional.of(30.0));
        when(gcp.getMonthlyPrice("Cloud SQL")).thenReturn(Optional.empty());

        Map<String, Object> result = new CostService(azure, gcp, meters).process(List.of("EC2", "RDS"));

        // Azure: 1.25 live + 16.0 baseline; GCP: 30.0 csv + 14.0 baseline
        assertThat(cost(result)).containsEntry("azure", 17.25).containsEntry("gcp", 44.0);
        assertThat(result.get("best")).isEqualTo("Azure");
        assertThat(sources(result)).containsEntry("azure", "live (Azure Retail Prices API)")
                .containsEntry("gcp", "csv export");
    }

    @Test
    void countsAzureFallbacksPerService() {
        when(azure.getMonthlyPrice("Virtual Machines")).thenReturn(Optional.of(30.0));
        when(azure.getMonthlyPrice("SQL Database")).thenReturn(Optional.empty());
        lenient().when(gcp.getMonthlyPrice(anyString())).thenReturn(Optional.empty());

        new CostService(azure, gcp, meters).process(List.of("EC2", "RDS"));

        assertThat(meters.get("pricing.azure.fallbacks").tag("service", "SQL Database").counter().count()).isEqualTo(1.0);
        assertThat(meters.find("pricing.azure.fallbacks").tag("service", "Virtual Machines").counter()).isNull();
    }

    @Test
    void awsWinsTies() {
        when(azure.getMonthlyPrice(anyString())).thenReturn(Optional.of(8.0));
        when(gcp.getMonthlyPrice(anyString())).thenReturn(Optional.of(8.0));

        assertThat(new CostService(azure, gcp, meters).process(List.of("EC2")).get("best")).isEqualTo("AWS");
    }

    @Test
    void roundsTotalsToCents() {
        when(azure.getMonthlyPrice(anyString())).thenReturn(Optional.of(1.0 / 3));
        lenient().when(gcp.getMonthlyPrice(anyString())).thenReturn(Optional.empty());

        Map<String, Object> result = new CostService(azure, gcp, meters).process(List.of("EC2", "S3"));

        assertThat(cost(result).get("azure")).isEqualTo(0.67);
    }

    @Test
    void unknownServicesAreIgnored() {
        noLivePrices();

        Map<String, Object> result = new CostService(azure, gcp, meters).process(List.of("EC2", "Kinesis"));

        assertThat(result.get("azure")).isEqualTo(List.of("Virtual Machines"));
        assertThat(cost(result)).containsEntry("aws", 8.0);
    }

    @Test
    void catalogIsConsistent() {
        for (String aws : ServiceCatalog.SUPPORTED_AWS_SERVICES) {
            Map<String, String> mapping = ServiceCatalog.SERVICE_MAPPING.get(aws);
            assertThat(mapping).as("mapping for %s", aws).isNotNull();
            assertThat(ServiceCatalog.AWS_BASELINE).containsKey(aws);
            assertThat(ServiceCatalog.AZURE_BASELINE).containsKey(mapping.get("azure"));
            assertThat(ServiceCatalog.GCP_BASELINE).containsKey(mapping.get("gcp"));
        }
    }

    private void noLivePrices() {
        lenient().when(azure.getMonthlyPrice(anyString())).thenReturn(Optional.empty());
        lenient().when(gcp.getMonthlyPrice(anyString())).thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> cost(Map<String, Object> result) {
        return (Map<String, Double>) result.get("cost");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> sources(Map<String, Object> result) {
        return (Map<String, String>) result.get("pricingSource");
    }
}
