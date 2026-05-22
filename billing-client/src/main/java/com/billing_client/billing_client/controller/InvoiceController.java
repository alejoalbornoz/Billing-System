package com.billing_client.billing_client.controller;

import com.billing_client.billing_client.dto.request.InvoiceRequestDTO;
import com.billing_client.billing_client.dto.response.InvoiceResponseDTO;
import com.billing_client.billing_client.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;  // ← interface, not implementation

    /**
     * Submits a new invoice to AFIP for authorization.
     *
     * POST /api/v1/invoices
     *
     * Flow:
     *   1. Spring validates the request body (@Valid)
     *   2. Service saves the invoice as PENDING
     *   3. Service calls AFIP via SOAP
     *   4. Service updates and returns the invoice as APPROVED or REJECTED
     *
     * Returns 201 CREATED with the invoice (approved or rejected).
     */
    @PostMapping
    public ResponseEntity<InvoiceResponseDTO> createInvoice(
            @Valid @RequestBody InvoiceRequestDTO request) {

        log.info("REST request to create invoice — issuer: {} | total: {}",
                request.getIssuerCuit(), request.getTotalAmount());

        InvoiceResponseDTO response = invoiceService.processInvoice(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves an invoice by its internal database ID.
     *
     * GET /api/v1/invoices/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponseDTO> getInvoiceById(@PathVariable Long id) {
        log.info("REST request to get invoice — id: {}", id);

        return ResponseEntity.ok(invoiceService.findById(id));
    }

    /**
     * Retrieves an invoice by the external ID sent to AFIP.
     * Useful for checking the status of a specific submission.
     *
     * GET /api/v1/invoices/external/{externalInvoiceId}
     */
    @GetMapping("/external/{externalInvoiceId}")
    public ResponseEntity<InvoiceResponseDTO> getInvoiceByExternalId(
            @PathVariable String externalInvoiceId) {

        log.info("REST request to get invoice — externalInvoiceId: {}", externalInvoiceId);

        return ResponseEntity.ok(invoiceService.findByExternalInvoiceId(externalInvoiceId));
    }

    /**
     * Retrieves all invoices stored in the database.
     *
     * GET /api/v1/invoices
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponseDTO>> getAllInvoices() {
        log.info("REST request to get all invoices");

        return ResponseEntity.ok(invoiceService.findAll());
    }
}