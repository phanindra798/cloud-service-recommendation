package com.cloudrec.service.pricing;

/** Monthly cost = fixedMonthly + unitPrice × driver quantity. */
public record PriceRule(Driver driver, double unitPrice, double fixedMonthly) {

    public static PriceRule of(Driver driver, double unitPrice) {
        return new PriceRule(driver, unitPrice, 0);
    }
}
