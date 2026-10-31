package com.cloudrec.service.pricing;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the app's users are. Picks the nearest region on each cloud and an
 * approximate regional price uplift over US list prices (applied to
 * list-price estimates only; live Azure prices are already regional).
 */
public enum Region {

    US("United States", "us-east-1", "eastus", "us-central1", 1.00),
    CANADA("Canada", "ca-central-1", "canadacentral", "northamerica-northeast1", 1.05),
    BRAZIL("Brazil / South America", "sa-east-1", "brazilsouth", "southamerica-east1", 1.45),
    UK("United Kingdom", "eu-west-2", "uksouth", "europe-west2", 1.12),
    EUROPE("Germany / EU", "eu-central-1", "germanywestcentral", "europe-west3", 1.12),
    INDIA("India", "ap-south-1", "centralindia", "asia-south1", 1.05),
    SINGAPORE("Singapore / Southeast Asia", "ap-southeast-1", "southeastasia", "asia-southeast1", 1.15),
    JAPAN("Japan", "ap-northeast-1", "japaneast", "asia-northeast1", 1.20),
    AUSTRALIA("Australia", "ap-southeast-2", "australiaeast", "australia-southeast1", 1.18),
    UAE("UAE / Middle East", "me-central-1", "uaenorth", "me-central1", 1.15);

    private final String label;
    private final String aws;
    private final String azure;
    private final String gcp;
    private final double priceMultiplier;

    Region(String label, String aws, String azure, String gcp, double priceMultiplier) {
        this.label = label;
        this.aws = aws;
        this.azure = azure;
        this.gcp = gcp;
        this.priceMultiplier = priceMultiplier;
    }

    public String label() { return label; }
    public String azure() { return azure; }
    public double priceMultiplier() { return priceMultiplier; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", name());
        map.put("label", label);
        map.put("aws", aws);
        map.put("azure", azure);
        map.put("gcp", gcp);
        map.put("priceMultiplier", priceMultiplier);
        return map;
    }
}
