package com.cloudrec.service.pricing;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
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

    /** ~1.16 peak req/s → 1 instance of everything; 1M requests; 10 thousand users. */
    private static final UsageProfile SMALL = new UsageProfile(10_000, 1_000_000, 100, 200);
    /** 155M req/month → ~179 peak req/s → 4 app instances, 1 DB, 1 cache node. */
    private static final UsageProfile LARGE = new UsageProfile(500_000, 155_000_000, 2_000, 10_000);

    @Mock
    private AzurePricingClient azure;
    @Mock
    private GcpCsvPricing gcp;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private CostService costs;

    @BeforeEach
    void setUp() {
        costs = new CostService(azure, gcp, meters);
        lenient().when(azure.supports(anyString())).thenReturn(false);
        lenient().when(gcp.getUnitPrice(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void mapsServicesToEquivalentsOnEveryProvider() {
        Map<String, Object> result = costs.process(List.of("EC2", "S3", "SQS"), SMALL);

        assertThat(result.get("aws")).isEqualTo(List.of("EC2", "S3", "SQS"));
        assertThat(result.get("azure")).isEqualTo(List.of("Virtual Machines", "Blob Storage", "Service Bus"));
        assertThat(result.get("gcp")).isEqualTo(List.of("Compute Engine", "Cloud Storage", "Pub/Sub"));
    }

    @Test
    void pricesEachServiceFromUsage() {
        Map<String, Object> result = costs.process(List.of("EC2", "S3", "CloudFront"), SMALL);

        // EC2: 1 instance × 30.37; S3: 100 GB × 0.023; CloudFront: 200 GB × 0.085
        assertThat(items(result, "aws")).extracting(i -> i.get("monthlyCost")).containsExactly(30.37, 2.3, 17.0);
        assertThat(cost(result)).containsEntry("aws", 49.67);

        Map<String, Object> s3 = items(result, "aws").get(1);
        assertThat(s3).containsEntry("service", "S3").containsEntry("quantity", 100.0)
                .containsEntry("unit", "GB").containsEntry("unitPrice", 0.023)
                .containsEntry("source", CostService.ESTIMATE);
    }

    @Test
    void costGrowsWithScale() {
        List<String> stack = List.of("EC2", "RDS", "ElastiCache", "S3", "CloudFront", "Cognito");

        Map<String, Object> small = costs.process(stack, SMALL);
        Map<String, Object> large = costs.process(stack, LARGE);

        Map<String, Object> ec2 = items(large, "aws").get(0);
        assertThat(ec2).containsEntry("quantity", 4.0).containsEntry("monthlyCost", 121.48);
        assertThat(items(large, "aws").get(1)).containsEntry("quantity", 1.0); // DB still fits on one instance
        assertThat(cost(large).get("aws")).isGreaterThan(cost(small).get("aws") * 10);
    }

    @Test
    void fixedMonthlyFeeIsAdded() {
        Map<String, Object> result = costs.process(List.of("SQS"), SMALL);

        // Service Bus: 9.81 base + 1M × 0.80
        assertThat(items(result, "azure").get(0)).containsEntry("monthlyCost", 10.61);
    }

    @Test
    void reportsUsageAndPeakLoad() {
        Map<String, Object> result = costs.process(List.of("EC2"), LARGE);

        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) result.get("usage");
        assertThat(usage).containsEntry("monthlyActiveUsers", 500_000L)
                .containsEntry("requestsPerMonth", 155_000_000L)
                .containsEntry("peakRequestsPerSecond", 179.4);
    }

    @Test
    void picksCheapestProvider() {
        Map<String, Object> result = costs.process(List.of("EC2", "RDS"), SMALL);

        // AWS 30.37 + 15.00, Azure 30.37 + 14.72, GCP 24.46 + 9.37
        assertThat(cost(result)).containsEntry("aws", 45.37).containsEntry("azure", 45.09).containsEntry("gcp", 33.83);
        assertThat(result.get("best")).isEqualTo("GCP");
        assertThat(sources(result)).containsEntry("aws", CostService.ESTIMATE)
                .containsEntry("azure", CostService.ESTIMATE)
                .containsEntry("gcp", CostService.ESTIMATE);
    }

    @Test
    void usesLiveAzureAndCsvGcpUnitPricesAndReportsSource() {
        when(azure.supports("Virtual Machines")).thenReturn(true);
        when(azure.getUnitPrice("Virtual Machines", "eastus")).thenReturn(Optional.of(20.0));
        when(gcp.getUnitPrice("Compute Engine")).thenReturn(Optional.of(50.0));

        Map<String, Object> result = costs.process(List.of("EC2"), LARGE);

        // 4 instances at the live/CSV unit price
        assertThat(cost(result)).containsEntry("azure", 80.0).containsEntry("gcp", 200.0);
        assertThat(items(result, "azure").get(0)).containsEntry("source", "live");
        assertThat(items(result, "gcp").get(0)).containsEntry("source", "csv");
        assertThat(sources(result)).containsEntry("azure", "live (Azure Retail Prices API)")
                .containsEntry("gcp", "csv export");
    }

    @Test
    void countsAzureFallbacksOnlyForServicesWithALiveLookup() {
        when(azure.supports("Virtual Machines")).thenReturn(true);
        when(azure.getUnitPrice("Virtual Machines", "eastus")).thenReturn(Optional.empty());

        costs.process(List.of("EC2", "Lambda"), SMALL);

        assertThat(meters.get("pricing.azure.fallbacks").tag("service", "Virtual Machines").counter().count()).isEqualTo(1.0);
        assertThat(meters.find("pricing.azure.fallbacks").tag("service", "Functions").counter()).isNull();
    }

    @Test
    void regionScalesListPricesAndPicksRegionalAzurePrices() {
        when(azure.supports("Virtual Machines")).thenReturn(true);
        when(azure.getUnitPrice("Virtual Machines", "centralindia")).thenReturn(Optional.of(25.0));

        Map<String, Object> result = costs.process(List.of("EC2"), SMALL, Region.INDIA, false);

        // AWS list price × 1.05 India uplift; Azure live price for centralindia used as-is
        assertThat(items(result, "aws").get(0)).containsEntry("unitPrice", 31.8885).containsEntry("monthlyCost", 31.89);
        assertThat(items(result, "azure").get(0)).containsEntry("unitPrice", 25.0);
        @SuppressWarnings("unchecked")
        Map<String, Object> region = (Map<String, Object>) result.get("region");
        assertThat(region).containsEntry("code", "INDIA").containsEntry("aws", "ap-south-1")
                .containsEntry("azure", "centralindia").containsEntry("gcp", "asia-south1");
    }

    @Test
    void highAvailabilityAddsRedundancyOnlyWhereItCosts() {
        List<String> stack = List.of("EC2", "RDS", "ElastiCache", "Lambda");

        Map<String, Object> single = costs.process(stack, SMALL, Region.US, false);
        Map<String, Object> ha = costs.process(stack, SMALL, Region.US, true);

        assertThat(items(ha, "aws")).extracting(i -> i.get("quantity")).containsExactly(2.0, 2.0, 2.0, 1.0);
        assertThat(items(single, "aws")).extracting(i -> i.get("quantity")).containsExactly(1.0, 1.0, 1.0, 1.0);
        assertThat(ha.get("highAvailability")).isEqualTo(true);
    }

    @Test
    void awsWinsTies() {
        when(gcp.getUnitPrice("Compute Engine")).thenReturn(Optional.of(30.37));

        assertThat(costs.process(List.of("EC2"), SMALL).get("best")).isEqualTo("AWS");
    }

    @Test
    void unknownServicesAreIgnored() {
        Map<String, Object> result = costs.process(List.of("EC2", "Kinesis"), SMALL);

        assertThat(result.get("azure")).isEqualTo(List.of("Virtual Machines"));
        assertThat(items(result, "aws")).hasSize(1);
    }

    @Test
    void catalogIsConsistent() {
        assertThat(ServiceCatalog.SERVICE_MAPPING.keySet())
                .containsExactlyInAnyOrderElementsOf(ServiceCatalog.SUPPORTED_AWS_SERVICES);
        for (String aws : ServiceCatalog.SUPPORTED_AWS_SERVICES) {
            Map<String, String> mapping = ServiceCatalog.SERVICE_MAPPING.get(aws);
            PriceRule awsRule = ServiceCatalog.AWS_PRICES.get(aws);
            PriceRule azureRule = ServiceCatalog.AZURE_PRICES.get(mapping.get("azure"));
            PriceRule gcpRule = ServiceCatalog.GCP_PRICES.get(mapping.get("gcp"));

            assertThat(awsRule).as("AWS price for %s", aws).isNotNull();
            assertThat(azureRule).as("Azure price for %s", aws).isNotNull();
            assertThat(gcpRule).as("GCP price for %s", aws).isNotNull();
            // Equivalent services must be billed by the same driver to be comparable
            assertThat(azureRule.driver()).as("Azure driver for %s", aws).isEqualTo(awsRule.driver());
            assertThat(gcpRule.driver()).as("GCP driver for %s", aws).isEqualTo(awsRule.driver());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> result, String provider) {
        return ((Map<String, List<Map<String, Object>>>) result.get("breakdown")).get(provider);
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
