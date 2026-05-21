package com.billing_client.billing_client.dto.response;

import com.billing_client.billing_client.model.enums.InvoiceStatus;
import com.billing_client.billing_client.model.enums.InvoiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponseDTO {

    private Long id;
    private String externalInvoiceId;

    // ─────────────────────────────────────────────
    //  INVOICE CLASSIFICATION
    // ─────────────────────────────────────────────

    private InvoiceType invoiceType;
    private LocalDate issueDate;
    private Integer pointOfSale;

    // ─────────────────────────────────────────────
    //  ISSUER
    // ─────────────────────────────────────────────

    private String issuerCuit;
    private String issuerBusinessName;
    private String issuerAddress;

    // ─────────────────────────────────────────────
    //  RECEIVER
    // ─────────────────────────────────────────────

    private String receiverCuit;
    private String receiverBusinessName;
    private String receiverAddress;

    // ─────────────────────────────────────────────
    //  AMOUNTS
    // ─────────────────────────────────────────────

    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;

    // ─────────────────────────────────────────────
    //  AFIP RESPONSE
    // ─────────────────────────────────────────────

    private InvoiceStatus status;
    private String cae;
    private LocalDate caeExpirationDate;
    private Long afipInvoiceNumber;
    private String afipMessage;

    // ─────────────────────────────────────────────
    //  ITEMS & AUDIT
    // ─────────────────────────────────────────────

    private List<InvoiceItemResponseDTO> items;
    private LocalDateTime createdAt;
}
