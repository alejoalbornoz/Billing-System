package com.afip.soap.server.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.InvoiceItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class InvoiceValidator {

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
        validateIssuer(request, errors);
        validateReceiver(request, errors);
        validateItems(request, errors);
        validateAmounts(request, errors);

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

    private void validateIssuer(AuthorizeInvoiceRequest request,
                                List<ValidationError> errors) {
        if (request.getIssuer() == null) {
            errors.add(new ValidationError("ERR_002", "Issuer information is required."));
            return; // no point validating fields if the object itself is null
        }

        if (isBlank(request.getIssuer().getCuit())) {
            errors.add(new ValidationError("ERR_003", "Issuer CUIT is required."));
        } else if (!isValidCuit(request.getIssuer().getCuit())) {
            errors.add(new ValidationError("ERR_004",
                    "Issuer CUIT format is invalid. Expected format: XX-XXXXXXXX-X"));
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
        } else if (!isValidCuit(request.getReceiver().getCuit())) {
            errors.add(new ValidationError("ERR_008",
                    "Receiver CUIT format is invalid. Expected format: XX-XXXXXXXX-X"));
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

    // ─────────────────────────────────────────────
    //  HELPER METHODS
    // ─────────────────────────────────────────────

    /**
     * Validates Argentine CUIT format: XX-XXXXXXXX-X
     * Example valid CUITs: 20-12345678-9 / 30-71234567-0
     */
    private boolean isValidCuit(String cuit) {
        return cuit != null && cuit.matches("\\d{2}-\\d{8}-\\d{1}");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}