package com.cloudrec.service.pricing;

/**
 * Typical per-user monthly footprint by kind of app. With a user count this
 * gives requests, storage and data transfer without guessing each one.
 */
public enum AppType {

    WEB_APP("Website / web app", 300, 5, 50),
    ECOMMERCE("E-commerce store", 400, 10, 100),
    MOBILE_BACKEND("Mobile app backend / API", 600, 5, 30),
    SAAS("B2B SaaS / dashboard", 1_500, 50, 100),
    CHAT("Real-time chat / messaging", 3_000, 50, 200),
    SOCIAL("Social / media sharing", 1_500, 200, 1_500),
    VIDEO_STREAMING("Video streaming", 500, 20, 8_000),
    BLOG("Blog / content site", 50, 1, 20),
    ANALYTICS("Data / analytics pipeline", 5_000, 500, 20);

    /** Fixed storage for code, config and indexes regardless of user count. */
    static final long BASE_STORAGE_GB = 10;

    private final String label;
    private final long requestsPerUser;
    private final double storageMbPerUser;
    private final double transferMbPerUser;

    AppType(String label, long requestsPerUser, double storageMbPerUser, double transferMbPerUser) {
        this.label = label;
        this.requestsPerUser = requestsPerUser;
        this.storageMbPerUser = storageMbPerUser;
        this.transferMbPerUser = transferMbPerUser;
    }

    public String label() { return label; }

    long requests(long users) {
        return users * requestsPerUser;
    }

    long storageGb(long users) {
        return BASE_STORAGE_GB + Math.round(users * storageMbPerUser / 1024);
    }

    long transferGb(long users) {
        return Math.max(1, Math.round(users * transferMbPerUser / 1024));
    }
}
