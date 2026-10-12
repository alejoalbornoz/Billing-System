package com.billing_client.billing_client.model.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum VatRate {
    EXEMPT("0"),        //  0% - Exento
    ZERO("0"),          //  0% - No gravado
    VAT_10_5("0.105"),  // 10.5%
    VAT_21("0.21"),     // 21%
    VAT_27("0.27");     // 27%

    private final BigDecimal rate;

    VatRate(String rate) {
        this.rate = new BigDecimal(rate);
    }

    /**
     * VAT for the given taxable base, rounded to 2 decimals.
     */
    public BigDecimal vatFor(BigDecimal taxableBase) {
        return taxableBase.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
