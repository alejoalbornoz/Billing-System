package com.afip.soap.server.validator;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoiceValidator Tests")
class InvoiceValidatorTest {

    private InvoiceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InvoiceValidator();
    }

    // ─────────────────────────────────────────────
    //  HAPPY PATH
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return no errors when invoice is valid")
    void shouldReturnNoErrors_whenInvoiceIsValid() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).isEmpty();
    }

    // ─────────────────────────────────────────────
    //  EXTERNAL INVOICE ID
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_001 when externalInvoiceId is null")
    void shouldReturnErr001_whenExternalInvoiceIdIsNull() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setExternalInvoiceId(null);

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_001");
    }

    @Test
    @DisplayName("Should return ERR_001 when externalInvoiceId is blank")
    void shouldReturnErr001_whenExternalInvoiceIdIsBlank() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setExternalInvoiceId("   ");

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_001");
    }

    // ─────────────────────────────────────────────
    //  ISSUER
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_002 when issuer is null")
    void shouldReturnErr002_whenIssuerIsNull() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setIssuer(null);

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_002");
    }

    @Test
    @DisplayName("Should return ERR_004 when issuer CUIT format is invalid")
    void shouldReturnErr004_whenIssuerCuitFormatIsInvalid() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getIssuer().setCuit("12345");

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_004");
    }

    @Test
    @DisplayName("Should return ERR_005 when issuer business name is blank")
    void shouldReturnErr005_whenIssuerBusinessNameIsBlank() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getIssuer().setBusinessName("");

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_005");
    }

    // ─────────────────────────────────────────────
    //  RECEIVER
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_008 when receiver CUIT format is invalid")
    void shouldReturnErr008_whenReceiverCuitFormatIsInvalid() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getReceiver().setCuit("invalid-cuit");

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_008");
    }

    // ─────────────────────────────────────────────
    //  CUIT CHECK DIGIT (MODULO 11)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_022 when issuer CUIT has a wrong check digit")
    void shouldReturnErr022_whenIssuerCuitCheckDigitIsWrong() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getIssuer().setCuit("30-71234567-0"); // valid check digit is 1

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").containsExactly("ERR_022");
    }

    @Test
    @DisplayName("Should return ERR_023 when receiver CUIT has a wrong check digit")
    void shouldReturnErr023_whenReceiverCuitCheckDigitIsWrong() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getReceiver().setCuit("20-12345678-9"); // valid check digit is 6

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").containsExactly("ERR_023");
    }

    @Test
    @DisplayName("Should accept a CUIT whose check digit is 0 (11 - remainder = 11)")
    void shouldAcceptCuit_whenCheckDigitIsZero() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getReceiver().setCuit("20-10000013-0");

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should reject a CUIT whose check digit would be 10, which AFIP never issues")
    void shouldRejectCuit_whenCheckDigitWouldBeTen() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();

        for (int digit = 0; digit <= 9; digit++) {
            request.getReceiver().setCuit("20-10000005-" + digit);

            assertThat(validator.validate(request)).extracting("code").containsExactly("ERR_023");
        }
    }

    // ─────────────────────────────────────────────
    //  POINT OF SALE
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_024 when point of sale is missing")
    void shouldReturnErr024_whenPointOfSaleIsMissing() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setPointOfSale(null);

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").containsExactly("ERR_024");
    }

    @Test
    @DisplayName("Should return ERR_024 when point of sale is out of range")
    void shouldReturnErr024_whenPointOfSaleIsOutOfRange() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setPointOfSale(BigInteger.valueOf(100_000));

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").containsExactly("ERR_024");
    }

    // ─────────────────────────────────────────────
    //  ITEMS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_010 when items list is empty")
    void shouldReturnErr010_whenItemsListIsEmpty() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getItems().getItem().clear();

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_010");
    }

    @Test
    @DisplayName("Should return ERR_012 when item quantity is zero")
    void shouldReturnErr012_whenItemQuantityIsZero() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getItems().getItem().get(0).setQuantity(BigDecimal.ZERO);

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_012");
    }

    @Test
    @DisplayName("Should return ERR_013 when item unit price is negative")
    void shouldReturnErr013_whenItemUnitPriceIsNegative() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getItems().getItem().get(0).setUnitPrice(new BigDecimal("-1.00"));

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_013");
    }

    // ─────────────────────────────────────────────
    //  AMOUNTS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_018 when totalAmount does not match netAmount + vatAmount")
    void shouldReturnErr018_whenTotalAmountDoesNotMatch() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setTotalAmount(new BigDecimal("9999.00")); // wrong total

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_018");
    }

    @Test
    @DisplayName("Should return ERR_015 when netAmount is zero")
    void shouldReturnErr015_whenNetAmountIsZero() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setNetAmount(BigDecimal.ZERO);

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_015");
    }

    // ─────────────────────────────────────────────
    //  AMOUNTS vs ITEMS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return ERR_019 when item subtotal is not quantity * unitPrice")
    void shouldReturnErr019_whenItemSubtotalDoesNotMatch() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.getItems().getItem().get(0).setSubtotal(new BigDecimal("1.00"));

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").contains("ERR_019");
    }

    @Test
    @DisplayName("Should reject amounts that are consistent with each other but not with the items")
    void shouldReturnErr020AndErr021_whenAmountsDoNotMatchItems() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        // Items add up to 1000.00 + 21% VAT, but the invoice declares 1.00 + 0.21
        request.setNetAmount(new BigDecimal("1.00"));
        request.setVatAmount(new BigDecimal("0.21"));
        request.setTotalAmount(new BigDecimal("1.21"));

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").containsExactly("ERR_020", "ERR_021");
    }

    @Test
    @DisplayName("Should return only ERR_021 when VAT does not match the item rates")
    void shouldReturnErr021_whenVatDoesNotMatchRates() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        // 10% instead of 21% — total is still net + VAT, so only the VAT check fails
        request.setVatAmount(new BigDecimal("100.00"));
        request.setTotalAmount(new BigDecimal("1100.00"));

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).extracting("code").containsExactly("ERR_021");
    }

    @Test
    @DisplayName("Should accept an invoice with items of different VAT rates")
    void shouldReturnNoErrors_whenItemsHaveMixedRates() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();

        server.soap.afip.invoice.InvoiceItem book = new server.soap.afip.invoice.InvoiceItem();
        book.setDescription("Libro técnico");
        book.setQuantity(new BigDecimal("2"));
        book.setUnitPrice(new BigDecimal("25.00"));
        book.setVatRate(server.soap.afip.invoice.VatRate.VAT_10_5);
        book.setSubtotal(new BigDecimal("50.00"));
        request.getItems().getItem().add(book);

        // 1000.00 @ 21% + 50.00 @ 10.5% → net 1050.00, VAT 210.00 + 5.25
        request.setNetAmount(new BigDecimal("1050.00"));
        request.setVatAmount(new BigDecimal("215.25"));
        request.setTotalAmount(new BigDecimal("1265.25"));

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).isEmpty();
    }

    // ─────────────────────────────────────────────
    //  MULTIPLE ERRORS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return multiple errors when multiple fields are invalid")
    void shouldReturnMultipleErrors_whenMultipleFieldsAreInvalid() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildValidRequest();
        request.setExternalInvoiceId(null);
        request.getIssuer().setCuit("bad-cuit");
        request.getItems().getItem().get(0).setQuantity(BigDecimal.ZERO);

        List<InvoiceValidator.ValidationError> errors = validator.validate(request);

        assertThat(errors).hasSizeGreaterThanOrEqualTo(3);
        assertThat(errors).extracting("code")
                .contains("ERR_001", "ERR_004", "ERR_012");
    }

    // ─────────────────────────────────────────────
    //  BUILDER
    // ─────────────────────────────────────────────

    private server.soap.afip.invoice.AuthorizeInvoiceRequest buildValidRequest() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = new server.soap.afip.invoice.AuthorizeInvoiceRequest();
        request.setExternalInvoiceId("test-uuid-1234");
        request.setInvoiceType(server.soap.afip.invoice.InvoiceType.INVOICE_B);
        request.setPointOfSale(BigInteger.ONE);

        server.soap.afip.invoice.TaxpayerInfo issuer = new server.soap.afip.invoice.TaxpayerInfo();
        issuer.setCuit("30-71234567-1");
        issuer.setBusinessName("Mi Empresa SRL");
        request.setIssuer(issuer);

        server.soap.afip.invoice.TaxpayerInfo receiver = new server.soap.afip.invoice.TaxpayerInfo();
        receiver.setCuit("20-12345678-6");
        receiver.setBusinessName("Cliente SA");
        request.setReceiver(receiver);

        server.soap.afip.invoice.InvoiceItem item = new server.soap.afip.invoice.InvoiceItem();
        item.setDescription("Servicio de software");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setVatRate(server.soap.afip.invoice.VatRate.VAT_21);
        item.setSubtotal(new BigDecimal("1000.00"));

        server.soap.afip.invoice.AuthorizeInvoiceRequest.Items items = new server.soap.afip.invoice.AuthorizeInvoiceRequest.Items();
        items.getItem().add(item);
        request.setItems(items);

        request.setNetAmount(new BigDecimal("1000.00"));
        request.setVatAmount(new BigDecimal("210.00"));
        request.setTotalAmount(new BigDecimal("1210.00"));

        return request;
    }
}