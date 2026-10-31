package com.cloudrec.dto;

import lombok.Data;

/** Only {@code problem} is required; the rest refine sizing and pricing. */
@Data
public class RecommendRequest {
    private String problem;
    /** {@link com.cloudrec.service.pricing.Region} code, e.g. "INDIA". */
    private String region;
    private Long monthlyActiveUsers;
    /** {@link com.cloudrec.service.pricing.AppType} code, e.g. "ECOMMERCE". */
    private String appType;
    private Long storageGb;
    private Long dataTransferGb;
    private Boolean highAvailability;
}
