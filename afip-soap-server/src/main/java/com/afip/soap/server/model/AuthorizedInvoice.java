package com.afip.soap.server.model;

import jakarta.persistence.*;
import lombok.*;
import server.soap.afip.invoice.InvoiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An invoice authorized by AFIP — the mock's own registry.
 *
 * Invoices are numbered correlatively per series (issuer CUIT + point of sale + type),
 * and the unique constraint guarantees no number is ever assigned twice.
 */
@Entity
@Table(
        name = "authorized_invoices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_invoice_series_number",
                columnNames = {"issuer_cuit", "point_of_sale", "invoice_type", "invoice_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizedInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID sent by the client. Unique, so a resent invoice is recognized. */
    @Column(name = "external_invoice_id", nullable = false, unique = true)
    private String externalInvoiceId;

    @Column(name = "issuer_cuit", nullable = false)
    private String issuerCuit;

    @Column(name = "point_of_sale", nullable = false)
    private Integer pointOfSale;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private InvoiceType invoiceType;

    @Column(name = "invoice_number", nullable = false)
    private Long invoiceNumber;

    @Column(name = "cae", nullable = false, length = 14)
    private String cae;

    @Column(name = "cae_expiration_date", nullable = false)
    private LocalDate caeExpirationDate;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "authorized_at", nullable = false)
    private LocalDateTime authorizedAt;
}
