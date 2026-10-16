package com.afip.soap.server.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.InvoiceItem;
import server.soap.afip.invoice.VatRate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InvoiceValidator {

    private static final int[] CUIT_WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private static final BigInteger MAX_POINT_OF_SALE = BigInteger.valueOf(99_999);

    /** VAT percentage for each rate of the XSD contract. */
    private static final Map<VatRate, BigDecimal> VAT_RATES = new EnumMap<>(Map.of(
            VatRate.EXEMPT,   BigDecimal.ZERO,
            VatRate.ZERO,     BigDecimal.ZERO,
            VatRate.VAT_10_5, new BigDecimal("0.105"),
            VatRate.VAT_21,   new BigDecimal("0.21"),
            VatRate.VAT_27,   new BigDecimal("0.27")
    ));

    /**
     * Represents a single validation error.
     *
     * We use a Java record here — it's a compact way to define
     * an immutable data class (available since Java 16).
     *
     * Equivalent to:
     *   class ValidationError {
     *       private final String code;
     *       private final String description;
     *       // constructor, getters, equals, hashCode, toString...
     *   }
     */
    public record ValidationError(String code, String description) {}

    // ─────────────────────────────────────────────
    //  MAIN ENTRY POINT
    // ─────────────────────────────────────────────

    /**
     * Runs all validation rules against the incoming invoice request.
     * Returns a list of errors — empty list means the invoice is valid.
     */
    public List<ValidationError> validate(AuthorizeInvoiceRequest request) {
        List<ValidationError> errors = new ArrayList<>();

        validateExternalInvoiceId(request, errors);
        validatePointOfSale(request, errors);
        validateIssuer(request, errors);
        validateReceiver(request, errors);
        validateItems(request, errors);
        validateAmounts(request, errors);
        validateAmountsMatchItems(request, errors);

        if (!errors.isEmpty()) {
            log.warn("Validation failed for externalInvoiceId: {} — {} error(s) found",
                    request.getExternalInvoiceId(), errors.size());
        }

        return errors;
    }

    // ─────────────────────────────────────────────
    //  VALIDATION RULES
    // ─────────────────────────────────────────────

    private void validateExternalInvoiceId(AuthorizeInvoiceRequest request,
                                           List<ValidationError> errors) {
        if (isBlank(request.getExternalInvoiceId())) {
            errors.add(new ValidationError(
                    "ERR_001",
                    "External invoice ID is required."
            ));
        }
    }

    /**
     * Invoices are numbered per point of sale, so it is required (AFIP accepts 1 to 99999).
     */
    private void validatePointOfSale(AuthorizeInvoiceRequest request,
                                     List<ValidationError> errors) {
        BigInteger pointOfSale = request.getPointOfSale();

        if (pointOfSale == null
                || pointOfSale.compareTo(BigInteger.ONE) < 0
                || pointOfSale.compareTo(MAX_POINT_OF_SALE) > 0) {
            errors.add(new ValidationError(
                    "ERR_024",
                    "Point of sale is required and must be between 1 and 99999."
            ));
        }
    }

    private void validateIssuer(AuthorizeInvoiceRequest request,
                                List<ValidationError> errors) {
        if (request.getIssuer() == null) {
            errors.add(new ValidationError("ERR_002", "Issuer information is required."));
            return; // no point validating fields if the object itself is null
        }

        if (isBlank(request.getIssuer().getCuit())) {
            errors.add(new ValidationError("ERR_003", "Issuer CUIT is required."));
        } else if (!isValidCuitFormat(request.getIssuer().getCuit())) {
            errors.add(new ValidationError("ERR_004",
                    "Issuer CUIT format is invalid. Expected format: XX-XXXXXXXX-X"));
        } else if (!hasValidCuitCheckDigit(request.getIssuer().getCuit())) {
            errors.add(new ValidationError("ERR_022", "Issuer CUIT check digit is invalid."));
        }

        if (isBlank(request.getIssuer().getBusinessName())) {
            errors.add(new ValidationError("ERR_005", "Issuer business name is required."));
        }
    }

    private void validateReceiver(AuthorizeInvoiceRequest request,
                                  List<ValidationError> errors) {
        if (request.getReceiver() == null) {
            errors.add(new ValidationError("ERR_006", "Receiver information is required."));
            return;
        }

        if (isBlank(request.getReceiver().getCuit())) {
            errors.add(new ValidationError("ERR_007", "Receiver CUIT is required."));
        } else if (!isValidCuitFormat(request.getReceiver().getCuit())) {
            errors.add(new ValidationError("ERR_008",
                    "Receiver CUIT format is invalid. Expected format: XX-XXXXXXXX-X"));
        } else if (!hasValidCuitCheckDigit(request.getReceiver().getCuit())) {
            errors.add(new ValidationError("ERR_023", "Receiver CUIT check digit is invalid."));
        }

        if (isBlank(request.getReceiver().getBusinessName())) {
            errors.add(new ValidationError("ERR_009", "Receiver business name is required."));
        }
    }

    private void validateItems(AuthorizeInvoiceRequest request,
                               List<ValidationError> errors) {
        if (request.getItems() == null || request.getItems().getItem().isEmpty()) {
            errors.add(new ValidationError(
                    "ERR_010",
                    "Invoice must contain at least one item."
            ));
            return;
        }

        List<InvoiceItem> items = request.getItems().getItem();

        for (int i = 0; i < items.size(); i++) {
            InvoiceItem item = items.get(i);
            String prefix = "Item[" + (i + 1) + "]";

            if (isBlank(item.getDescription())) {
                errors.add(new ValidationError(
                        "ERR_011",
                        prefix + " description is required."
                ));
            }

            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(new ValidationError(
                        "ERR_012",
                        prefix + " quantity must be greater than zero."
                ));
            }

            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new ValidationError(
                        "ERR_013",
                        prefix + " unit price cannot be negative."
                ));
            }

            if (item.getVatRate() == null) {
                errors.add(new ValidationError(
                        "ERR_014",
                        prefix + " VAT rate is required."
                ));
            }

            if (item.getQuantity() != null && item.getUnitPrice() != null) {
                BigDecimal expectedSubtotal = item.getQuantity()
                        .multiply(item.getUnitPrice())
                        .setScale(2, RoundingMode.HALF_UP);

                if (item.getSubtotal() == null || item.getSubtotal().compareTo(expectedSubtotal) != 0) {
                    errors.add(new ValidationError(
                            "ERR_019",
                            prefix + " subtotal (" + item.getSubtotal() + ") does not match " +
                                    "quantity * unitPrice (" + expectedSubtotal + ")."
                    ));
                }
            }
        }
    }

    private void validateAmounts(AuthorizeInvoiceRequest request,
                                 List<ValidationError> errors) {
        if (request.getNetAmount() == null || request.getNetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ValidationError(
                    "ERR_015",
                    "Net amount must be greater than zero."
            ));
        }

        if (request.getVatAmount() == null || request.getVatAmount().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ValidationError(
                    "ERR_016",
                    "VAT amount cannot be negative."
            ));
        }

        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ValidationError(
                    "ERR_017",
                    "Total amount must be greater than zero."
            ));
        }

        // Cross-check: totalAmount must equal netAmount + vatAmount
        if (request.getNetAmount() != null
                && request.getVatAmount() != null
                && request.getTotalAmount() != null) {

            BigDecimal expectedTotal = request.getNetAmount().add(request.getVatAmount());

            if (request.getTotalAmount().compareTo(expectedTotal) != 0) {
                errors.add(new ValidationError(
                        "ERR_018",
                        "Total amount (" + request.getTotalAmount() + ") does not match " +
                                "netAmount + vatAmount (" + expectedTotal + ")."
                ));
            }
        }
    }

    /**
     * Recalculates net and VAT from the items instead of trusting the client.
     *
     * VAT is calculated once per rate over the sum of the subtotals with that rate,
     * rounded to 2 decimals — the client must use the same method.
     */
    private void validateAmountsMatchItems(AuthorizeInvoiceRequest request,
                                           List<ValidationError> errors) {
        if (request.getItems() == null || request.getItems().getItem().isEmpty()) {
            return;
        }

        List<InvoiceItem> items = request.getItems().getItem();

        // Missing subtotals or rates were already reported by validateItems
        if (items.stream().anyMatch(item -> item.getSubtotal() == null || item.getVatRate() == null)) {
            return;
        }

        Map<VatRate, BigDecimal> taxableBaseByRate = items.stream()
                .collect(Collectors.groupingBy(
                        InvoiceItem::getVatRate,
                        () -> new EnumMap<>(VatRate.class),
                        Collectors.reducing(BigDecimal.ZERO, InvoiceItem::getSubtotal, BigDecimal::add)));

        BigDecimal expectedNet = taxableBaseByRate.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedVat = taxableBaseByRate.entrySet().stream()
                .map(entry -> entry.getValue()
                        .multiply(VAT_RATES.get(entry.getKey()))
                        .setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (request.getNetAmount() != null && request.getNetAmount().compareTo(expectedNet) != 0) {
            errors.add(new ValidationError(
                    "ERR_020",
                    "Net amount (" + request.getNetAmount() + ") does not match " +
                            "the sum of item subtotals (" + expectedNet + ")."
            ));
        }

        if (request.getVatAmount() != null && request.getVatAmount().compareTo(expectedVat) != 0) {
            errors.add(new ValidationError(
                    "ERR_021",
                    "VAT amount (" + request.getVatAmount() + ") does not match " +
                            "the VAT calculated from the items (" + expectedVat + ")."
            ));
        }
    }

    // ─────────────────────────────────────────────
    //  HELPER METHODS
    // ─────────────────────────────────────────────

    /**
     * Validates Argentine CUIT format: XX-XXXXXXXX-X
     * Example valid CUITs: 20-12345678-6 / 30-71234567-1
     */
    private boolean isValidCuitFormat(String cuit) {
        return cuit != null && cuit.matches("\\d{2}-\\d{8}-\\d{1}");
    }

    /**
     * Validates the CUIT check digit (modulo 11), assuming the format is already valid.
     *
     * Each of the first 10 digits is multiplied by 5,4,3,2,7,6,5,4,3,2 and the products
     * are added up. The check digit is 11 minus the remainder of dividing that sum by 11:
     * 11 becomes 0, and 10 is impossible (AFIP never issues those CUITs).
     */
    private boolean hasValidCuitCheckDigit(String cuit) {
        String digits = cuit.replace("-", "");

        int sum = 0;
        for (int i = 0; i < CUIT_WEIGHTS.length; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * CUIT_WEIGHTS[i];
        }

        int expected = 11 - (sum % 11);
        if (expected == 11) {
            expected = 0;
        }

        return expected != 10 && expected == Character.getNumericValue(digits.charAt(10));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}