package com.billing_client.billing_client.dto.request;

import com.billing_client.billing_client.model.enums.InvoiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequestDTO {

    @NotNull(message = "Invoice type is required.")
    private InvoiceType invoiceType;

    @NotNull(message = "Issue date is required.")
    private LocalDate issueDate;

    @Min(value = 1, message = "Point of sale must be at least 1.")
    private Integer pointOfSale;

    // ─────────────────────────────────────────────
    //  ISSUER
    // ─────────────────────────────────────────────

    @NotBlank(message = "Issuer CUIT is required.")
    @Pattern(regexp = "\\d{2}-\\d{8}-\\d{1}", message = "Issuer CUIT format must be XX-XXXXXXXX-X")
    private String issuerCuit;

    @NotBlank(message = "Issuer business name is required.")
    private String issuerBusinessName;

    private String issuerAddress;

    // ─────────────────────────────────────────────
    //  RECEIVER
    // ─────────────────────────────────────────────

    @NotBlank(message = "Receiver CUIT is required.")
    @Pattern(regexp = "\\d{2}-\\d{8}-\\d{1}", message = "Receiver CUIT format must be XX-XXXXXXXX-X")
    private String receiverCuit;

    @NotBlank(message = "Receiver business name is required.")
    private String receiverBusinessName;

    private String receiverAddress;

    // ─────────────────────────────────────────────
    //  ITEMS
    // ─────────────────────────────────────────────

    @NotNull(message = "Items list is required.")
    @Size(min = 1, message = "Invoice must contain at least one item.")
    @Valid  // triggers validation on each InvoiceItemRequestDTO inside the list
    private List<InvoiceItemRequestDTO> items;

    // ─────────────────────────────────────────────
    //  AMOUNTS
    // ─────────────────────────────────────────────

    @NotNull(message = "Net amount is required.")
    @DecimalMin(value = "0.01", message = "Net amount must be greater than zero.")
    private BigDecimal netAmount;

    @NotNull(message = "VAT amount is required.")
    @DecimalMin(value = "0.00", message = "VAT amount cannot be negative.")
    private BigDecimal vatAmount;

    @NotNull(message = "Total amount is required.")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero.")
    private BigDecimal totalAmount;
}