package com.billing_client.billing_client.dto.request;

import com.billing_client.billing_client.model.enums.InvoiceType;
import com.billing_client.billing_client.validation.Cuit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    // AFIP numbers invoices per point of sale, so it is required
    @NotNull(message = "Point of sale is required.")
    @Min(value = 1, message = "Point of sale must be at least 1.")
    @Max(value = 99999, message = "Point of sale must be at most 99999.")
    private Integer pointOfSale;

    // ─────────────────────────────────────────────
    //  ISSUER
    // ─────────────────────────────────────────────

    @NotBlank(message = "Issuer CUIT is required.")
    @Cuit
    private String issuerCuit;

    @NotBlank(message = "Issuer business name is required.")
    private String issuerBusinessName;

    private String issuerAddress;

    // ─────────────────────────────────────────────
    //  RECEIVER
    // ─────────────────────────────────────────────

    @NotBlank(message = "Receiver CUIT is required.")
    @Cuit
    private String receiverCuit;

    @NotBlank(message = "Receiver business name is required.")
    private String receiverBusinessName;

    private String receiverAddress;

    // ─────────────────────────────────────────────
    //  ITEMS
    // ─────────────────────────────────────────────

    // Net, VAT and total amounts are not part of the request:
    // they are calculated from the items (see Invoice.calculateTotals).
    @NotNull(message = "Items list is required.")
    @Size(min = 1, message = "Invoice must contain at least one item.")
    @Valid  // triggers validation on each InvoiceItemRequestDTO inside the list
    private List<InvoiceItemRequestDTO> items;
}