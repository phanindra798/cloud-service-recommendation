package com.cloudrec.service.pricing;

import java.util.List;
import java.util.Map;

import static com.cloudrec.service.pricing.Driver.CACHE_NODE;
import static com.cloudrec.service.pricing.Driver.COMPUTE_INSTANCE;
import static com.cloudrec.service.pricing.Driver.DATABASE_INSTANCE;
import static com.cloudrec.service.pricing.Driver.MILLION_REQUESTS;
import static com.cloudrec.service.pricing.Driver.STORAGE_GB;
import static com.cloudrec.service.pricing.Driver.THOUSAND_USERS;
import static com.cloudrec.service.pricing.Driver.TRANSFER_GB;

/**
 * Central catalog: cross-provider service mapping and list-price estimates
 * (USD, on-demand, US regions, free tiers excluded). Each price is per unit of
 * the service's {@link Driver}; live sources replace the unit price when
 * available.
 *
 * Reference sizes: compute = 2 vCPU / 4 GB VM (t3.medium, B2s, e2-medium);
 * containers = 0.5 vCPU / 1 GB always on; database = smallest standard managed
 * tier; cache = smallest managed Redis node.
 */
public final class ServiceCatalog {

    private ServiceCatalog() {}

    public static final List<String> SUPPORTED_AWS_SERVICES = List.of(
            "EC2", "ECS Fargate", "Lambda", "S3", "RDS", "DynamoDB", "ElastiCache",
            "CloudFront", "API Gateway", "SQS", "Cognito"
    );

    /** AWS service → equivalent service per provider. */
    public static final Map<String, Map<String, String>> SERVICE_MAPPING = Map.ofEntries(
            Map.entry("EC2", Map.of("azure", "Virtual Machines", "gcp", "Compute Engine")),
            Map.entry("ECS Fargate", Map.of("azure", "Container Apps", "gcp", "Cloud Run")),
            Map.entry("Lambda", Map.of("azure", "Functions", "gcp", "Cloud Functions")),
            Map.entry("S3", Map.of("azure", "Blob Storage", "gcp", "Cloud Storage")),
            Map.entry("RDS", Map.of("azure", "SQL Database", "gcp", "Cloud SQL")),
            Map.entry("DynamoDB", Map.of("azure", "Cosmos DB", "gcp", "Firestore")),
            Map.entry("ElastiCache", Map.of("azure", "Cache for Redis", "gcp", "Memorystore")),
            Map.entry("CloudFront", Map.of("azure", "CDN", "gcp", "Cloud CDN")),
            Map.entry("API Gateway", Map.of("azure", "API Management", "gcp", "API Gateway")),
            Map.entry("SQS", Map.of("azure", "Service Bus", "gcp", "Pub/Sub")),
            Map.entry("Cognito", Map.of("azure", "Entra External ID", "gcp", "Identity Platform"))
    );

    public static final Map<String, PriceRule> AWS_PRICES = Map.ofEntries(
            Map.entry("EC2", PriceRule.of(COMPUTE_INSTANCE, 30.37)),
            Map.entry("ECS Fargate", PriceRule.of(COMPUTE_INSTANCE, 18.02)),
            Map.entry("Lambda", PriceRule.of(MILLION_REQUESTS, 1.03)),
            Map.entry("S3", PriceRule.of(STORAGE_GB, 0.023)),
            Map.entry("RDS", PriceRule.of(DATABASE_INSTANCE, 15.0)),
            Map.entry("DynamoDB", PriceRule.of(MILLION_REQUESTS, 0.50)),
            Map.entry("ElastiCache", PriceRule.of(CACHE_NODE, 12.41)),
            Map.entry("CloudFront", PriceRule.of(TRANSFER_GB, 0.085)),
            Map.entry("API Gateway", PriceRule.of(MILLION_REQUESTS, 1.00)),
            Map.entry("SQS", PriceRule.of(MILLION_REQUESTS, 0.40)),
            Map.entry("Cognito", PriceRule.of(THOUSAND_USERS, 5.50))
    );

    public static final Map<String, PriceRule> AZURE_PRICES = Map.ofEntries(
            Map.entry("Virtual Machines", PriceRule.of(COMPUTE_INSTANCE, 30.37)),
            Map.entry("Container Apps", PriceRule.of(COMPUTE_INSTANCE, 39.42)),
            Map.entry("Functions", PriceRule.of(MILLION_REQUESTS, 1.00)),
            Map.entry("Blob Storage", PriceRule.of(STORAGE_GB, 0.0184)),
            Map.entry("SQL Database", PriceRule.of(DATABASE_INSTANCE, 14.72)),
            Map.entry("Cosmos DB", PriceRule.of(MILLION_REQUESTS, 0.75)),
            Map.entry("Cache for Redis", PriceRule.of(CACHE_NODE, 16.06)),
            Map.entry("CDN", PriceRule.of(TRANSFER_GB, 0.081)),
            Map.entry("API Management", PriceRule.of(MILLION_REQUESTS, 3.50)),
            Map.entry("Service Bus", new PriceRule(MILLION_REQUESTS, 0.80, 9.81)),
            Map.entry("Entra External ID", PriceRule.of(THOUSAND_USERS, 3.25))
    );

    public static final Map<String, PriceRule> GCP_PRICES = Map.ofEntries(
            Map.entry("Compute Engine", PriceRule.of(COMPUTE_INSTANCE, 24.46)),
            Map.entry("Cloud Run", PriceRule.of(COMPUTE_INSTANCE, 28.91)),
            Map.entry("Cloud Functions", PriceRule.of(MILLION_REQUESTS, 1.20)),
            Map.entry("Cloud Storage", PriceRule.of(STORAGE_GB, 0.020)),
            Map.entry("Cloud SQL", PriceRule.of(DATABASE_INSTANCE, 9.37)),
            Map.entry("Firestore", PriceRule.of(MILLION_REQUESTS, 0.90)),
            Map.entry("Memorystore", PriceRule.of(CACHE_NODE, 35.77)),
            Map.entry("Cloud CDN", PriceRule.of(TRANSFER_GB, 0.080)),
            Map.entry("API Gateway", PriceRule.of(MILLION_REQUESTS, 3.00)),
            Map.entry("Pub/Sub", PriceRule.of(MILLION_REQUESTS, 0.04)),
            Map.entry("Identity Platform", PriceRule.of(THOUSAND_USERS, 5.50))
    );
}
