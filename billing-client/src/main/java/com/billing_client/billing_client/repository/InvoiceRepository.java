package com.billing_client.billing_client.repository;

import com.billing_client.billing_client.model.Invoice;
import com.billing_client.billing_client.model.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Finds an invoice by its external ID (the one sent to AFIP).
     * Useful to avoid duplicate submissions.
     */
    Optional<Invoice> findByExternalInvoiceId(String externalInvoiceId);

    /**
     * Finds all invoices by status.
     * Example: find all PENDING invoices to retry.
     */
    List<Invoice> findByStatus(InvoiceStatus status);

    /**
     * Finds all invoices from a specific issuer.
     */
    List<Invoice> findByIssuerCuit(String issuerCuit);

    /**
     * Finds all invoices from a specific receiver.
     */
    List<Invoice> findByReceiverCuit(String receiverCuit);

    /**
     * Finds all invoices issued between two dates.
     * Example: findByIssueDateBetween(2026-01-01, 2026-01-31)
     */
    List<Invoice> findByIssueDateBetween(LocalDate from, LocalDate to);

    /**
     * Checks if an invoice with this external ID already exists.
     * Used to prevent sending the same invoice to AFIP twice.
     */
    boolean existsByExternalInvoiceId(String externalInvoiceId);
}
