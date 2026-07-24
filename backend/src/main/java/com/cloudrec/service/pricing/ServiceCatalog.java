package com.cloudrec.service.pricing;

import java.util.List;
import java.util.Map;

/**
 * Central catalog: cross-provider service mapping and static baseline prices
 * (USD/month for a representative small workload). Baselines are used when a
 * live pricing source is unavailable.
 *
 * Usage profile assumed for estimates: 730 compute hours, 100 GB storage,
 * small managed DB instance, moderate CDN/API traffic.
 */
public final class ServiceCatalog {

    private ServiceCatalog() {}

    public static final List<String> SUPPORTED_AWS_SERVICES = List.of(
            "EC2", "S3", "RDS", "Lambda", "DynamoDB", "CloudFront", "API Gateway"
    );

    /** AWS service → equivalent service per provider. */
    public static final Map<String, Map<String, String>> SERVICE_MAPPING = Map.of(
            "EC2", Map.of("azure", "Virtual Machines", "gcp", "Compute Engine"),
            "S3", Map.of("azure", "Blob Storage", "gcp", "Cloud Storage"),
            "RDS", Map.of("azure", "SQL Database", "gcp", "Cloud SQL"),
            "Lambda", Map.of("azure", "Functions", "gcp", "Cloud Functions"),
            "DynamoDB", Map.of("azure", "Cosmos DB", "gcp", "Firestore"),
            "CloudFront", Map.of("azure", "CDN", "gcp", "Cloud CDN"),
            "API Gateway", Map.of("azure", "API Management", "gcp", "API Gateway")
    );

    public static final Map<String, Double> AWS_BASELINE = Map.of(
            "EC2", 8.0,
            "S3", 2.3,
            "RDS", 15.0,
            "Lambda", 2.0,
            "DynamoDB", 5.0,
            "CloudFront", 3.0,
            "API Gateway", 3.5
    );

    public static final Map<String, Double> AZURE_BASELINE = Map.of(
            "Virtual Machines", 9.0,
            "Blob Storage", 2.0,
            "SQL Database", 16.0,
            "Functions", 2.2,
            "Cosmos DB", 6.0,
            "CDN", 3.5,
            "API Management", 4.0
    );

    public static final Map<String, Double> GCP_BASELINE = Map.of(
            "Compute Engine", 7.0,
            "Cloud Storage", 2.6,
            "Cloud SQL", 14.0,
            "Cloud Functions", 2.1,
            "Firestore", 5.5,
            "Cloud CDN", 3.2,
            "API Gateway", 3.3
    );
}
