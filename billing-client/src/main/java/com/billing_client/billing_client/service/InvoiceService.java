package com.billing_client.billing_client.service;

import com.billing_client.billing_client.dto.request.InvoiceRequestDTO;
import com.billing_client.billing_client.dto.response.InvoiceResponseDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface InvoiceService {

    /**
     * Processes a new invoice:
     *   1. Calculates net, VAT and total amounts from the items
     *   2. Saves the invoice as PENDING (committed before calling AFIP)
     *   3. Sends it to AFIP via SOAP
     *   4. Updates the invoice with the AFIP response (APPROVED or REJECTED),
     *      or leaves it PENDING if AFIP could not be reached
     *   5. Returns the final invoice to the caller
     */
    InvoiceResponseDTO processInvoice(InvoiceRequestDTO request);

    /**
     * Resends to AFIP the invoices still PENDING that were created before the cutoff.
     *
     * @return how many invoices were resent
     */
    int resendPendingInvoices(LocalDateTime createdBefore);

    /**
     * Finds an invoice by its internal database ID.
     */
    InvoiceResponseDTO findById(Long id);

    /**
     * Finds an invoice by the external ID sent to AFIP.
     */
    InvoiceResponseDTO findByExternalInvoiceId(String externalInvoiceId);

    /**
     * Returns all invoices stored in the database.
     */
    List<InvoiceResponseDTO> findAll();
}
