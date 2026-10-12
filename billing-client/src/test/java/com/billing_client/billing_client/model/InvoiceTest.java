package com.billing_client.billing_client.model;

import com.billing_client.billing_client.model.enums.VatRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Invoice.calculateTotals Tests")
class InvoiceTest {

    @Test
    @DisplayName("Should calculate net, VAT and total for a single rate")
    void shouldCalculateTotals_forSingleRate() {
        Invoice invoice = invoiceWith(item("1000.00", VatRate.VAT_21));

        invoice.calculateTotals();

        assertThat(invoice.getNetAmount()).isEqualByComparingTo("1000.00");
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("210.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1210.00");
    }

    @Test
    @DisplayName("Should add VAT of each rate when items have different rates")
    void shouldCalculateTotals_forMixedRates() {
        Invoice invoice = invoiceWith(
                item("200.00", VatRate.VAT_21),
                item("50.00", VatRate.VAT_10_5),
                item("100.00", VatRate.EXEMPT));

        invoice.calculateTotals();

        // VAT = 200 * 21% + 50 * 10.5% + 100 * 0% = 42.00 + 5.25 + 0
        assertThat(invoice.getNetAmount()).isEqualByComparingTo("350.00");
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("47.25");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("397.25");
    }

    @Test
    @DisplayName("Should calculate VAT per rate, not per item, to avoid accumulating rounding")
    void shouldCalculateVatPerRate_notPerItem() {
        // Per item: 0.05 * 10.5% = 0.00525 → 0.01 each → 0.02 total
        // Per rate: 0.10 * 10.5% = 0.0105 → 0.01
        Invoice invoice = invoiceWith(
                item("0.05", VatRate.VAT_10_5),
                item("0.05", VatRate.VAT_10_5));

        invoice.calculateTotals();

        assertThat(invoice.getVatAmount()).isEqualByComparingTo("0.01");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("0.11");
    }

    @Test
    @DisplayName("Should return amounts with 2 decimals")
    void shouldReturnAmounts_withTwoDecimals() {
        Invoice invoice = invoiceWith(item("1000.00", VatRate.EXEMPT));

        invoice.calculateTotals();

        assertThat(invoice.getNetAmount().scale()).isEqualTo(2);
        assertThat(invoice.getVatAmount().scale()).isEqualTo(2);
        assertThat(invoice.getTotalAmount().scale()).isEqualTo(2);
    }

    // ─────────────────────────────────────────────
    //  BUILDERS
    // ─────────────────────────────────────────────

    private Invoice invoiceWith(InvoiceItem... items) {
        return Invoice.builder()
                .items(Arrays.asList(items))
                .build();
    }

    private InvoiceItem item(String subtotal, VatRate vatRate) {
        return InvoiceItem.builder()
                .description("Item")
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal(subtotal))
                .subtotal(new BigDecimal(subtotal))
                .vatRate(vatRate)
                .build();
    }
}
