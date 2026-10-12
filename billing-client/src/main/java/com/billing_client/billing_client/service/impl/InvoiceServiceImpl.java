package com.billing_client.billing_client.service.impl;

import com.billing_client.billing_client.dto.request.InvoiceItemRequestDTO;
import com.billing_client.billing_client.dto.request.InvoiceRequestDTO;
import com.billing_client.billing_client.dto.response.InvoiceItemResponseDTO;
import com.billing_client.billing_client.dto.response.InvoiceResponseDTO;
import com.billing_client.billing_client.exception.InvoiceNotFoundException;
import com.billing_client.billing_client.exception.SoapCommunicationException;
import com.billing_client.billing_client.model.Invoice;
import com.billing_client.billing_client.model.InvoiceItem;
import com.billing_client.billing_client.model.enums.InvoiceStatus;
import com.billing_client.billing_client.repository.InvoiceRepository;
import com.billing_client.billing_client.service.InvoiceService;
import com.billing_client.billing_client.soap.AfipSoapClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;

import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    static final String AFIP_UNAVAILABLE_MESSAGE =
            "AFIP could not be reached. The invoice will be resent automatically.";

    private final InvoiceRepository invoiceRepository;
    private final AfipSoapClient afipSoapClient;

    // ─────────────────────────────────────────────
    //  MAIN FLOW
    // ─────────────────────────────────────────────

    /**
     * Deliberately NOT @Transactional: each save() commits on its own, so the
     * PENDING invoice is stored before AFIP is called, and no DB connection
     * is held while waiting for the SOAP response.
     */
    @Override
    public InvoiceResponseDTO processInvoice(InvoiceRequestDTO request) {

        // Step 1 — generate a unique external ID for AFIP correlation
        String externalInvoiceId = UUID.randomUUID().toString();
        log.info("Processing new invoice — externalInvoiceId: {}", externalInvoiceId);

        // Step 2 — build the invoice (amounts calculated from the items) and save it as PENDING
        Invoice invoice = buildInvoice(externalInvoiceId, request);
        invoice = invoiceRepository.save(invoice);
        log.info("Invoice saved as PENDING — id: {} | total: {}",
                invoice.getId(), invoice.getTotalAmount());

        // Step 3 — send it to AFIP and store the result
        invoice = submitToAfip(invoice);

        // Step 4 — return the final invoice as DTO
        return toResponseDTO(invoice);
    }

    @Override
    public int resendPendingInvoices(LocalDateTime createdBefore) {
        List<Invoice> pendingInvoices = invoiceRepository
                .findByStatusAndCreatedAtBefore(InvoiceStatus.PENDING, createdBefore);

        pendingInvoices.forEach(this::submitToAfip);

        return pendingInvoices.size();
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDTO findById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found with id: " + id));
        return toResponseDTO(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDTO findByExternalInvoiceId(String externalInvoiceId) {
        Invoice invoice = invoiceRepository.findByExternalInvoiceId(externalInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Invoice not found with externalInvoiceId: " + externalInvoiceId));
        return toResponseDTO(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponseDTO> findAll() {
        return invoiceRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    //  AFIP SUBMISSION
    // ─────────────────────────────────────────────

    /**
     * Sends the invoice to AFIP and saves the outcome.
     *
     * If AFIP can't be reached the invoice was not rejected — it stays PENDING
     * and PendingInvoiceRetryJob resends it later.
     */
    private Invoice submitToAfip(Invoice invoice) {
        try {
            AuthorizeInvoiceResponse afipResponse = afipSoapClient.authorize(invoice);
            updateInvoiceWithAfipResponse(invoice, afipResponse);

            log.info("Invoice updated — id: {} | status: {}", invoice.getId(), invoice.getStatus());

        } catch (SoapCommunicationException e) {
            log.warn("AFIP unavailable, invoice stays PENDING — id: {} | error: {}",
                    invoice.getId(), e.getMessage());
            invoice.setAfipMessage(AFIP_UNAVAILABLE_MESSAGE);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Updates the invoice fields based on what AFIP returned.
     */
    private void updateInvoiceWithAfipResponse(Invoice invoice,
                                               AuthorizeInvoiceResponse afipResponse) {
        switch (afipResponse.getStatus()) {
            case APPROVED -> {
                invoice.setStatus(InvoiceStatus.APPROVED);
                invoice.setCae(afipResponse.getCae());
                invoice.setCaeExpirationDate(toLocalDate(afipResponse.getCaeExpirationDate()));
                invoice.setAfipInvoiceNumber(afipResponse.getAfipInvoiceNumber());
                invoice.setAfipMessage(afipResponse.getMessage());
            }
            case REJECTED -> {
                invoice.setStatus(InvoiceStatus.REJECTED);
                invoice.setAfipMessage(buildRejectionMessage(afipResponse));
            }
            // AFIP received it but hasn't decided yet — keep it PENDING
            case PENDING -> invoice.setAfipMessage(afipResponse.getMessage());
        }
    }

    /**
     * AFIP's generic message followed by each validation error,
     * so the caller can see why the invoice was rejected.
     *
     * Example: "Invoice rejected. Please review the errors. ERR_004: Issuer CUIT format is invalid."
     */
    private String buildRejectionMessage(AuthorizeInvoiceResponse afipResponse) {
        if (afipResponse.getErrors() == null || afipResponse.getErrors().getError().isEmpty()) {
            return afipResponse.getMessage();
        }

        String errors = afipResponse.getErrors().getError().stream()
                .map(error -> error.getCode() + ": " + error.getDescription())
                .collect(Collectors.joining(" | "));

        return afipResponse.getMessage() + " " + errors;
    }

    // ─────────────────────────────────────────────
    //  BUILDERS
    // ─────────────────────────────────────────────

    /**
     * Builds an Invoice entity from the request DTO.
     * Status starts as PENDING; amounts are calculated from the items.
     */
    private Invoice buildInvoice(String externalInvoiceId, InvoiceRequestDTO request) {
        Invoice invoice = Invoice.builder()
                .externalInvoiceId(externalInvoiceId)
                .invoiceType(request.getInvoiceType())
                .issueDate(request.getIssueDate())
                .pointOfSale(request.getPointOfSale())
                .issuerCuit(request.getIssuerCuit())
                .issuerBusinessName(request.getIssuerBusinessName())
                .issuerAddress(request.getIssuerAddress())
                .receiverCuit(request.getReceiverCuit())
                .receiverBusinessName(request.getReceiverBusinessName())
                .receiverAddress(request.getReceiverAddress())
                .status(InvoiceStatus.PENDING)
                .build();

        // Build and link each item to the invoice
        List<InvoiceItem> items = request.getItems().stream()
                .map(itemDTO -> buildInvoiceItem(itemDTO, invoice))
                .collect(Collectors.toList());

        invoice.setItems(items);
        invoice.calculateTotals();

        return invoice;
    }

    private InvoiceItem buildInvoiceItem(InvoiceItemRequestDTO dto, Invoice invoice) {
        BigDecimal subtotal = dto.getQuantity()
                .multiply(dto.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);

        return InvoiceItem.builder()
                .description(dto.getDescription())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .vatRate(dto.getVatRate())
                .subtotal(subtotal)
                .invoice(invoice)
                .build();
    }

    /**
     * Reads year/month/day directly instead of parsing toString(),
     * so a timezone suffix in the XML date (e.g. "2026-06-04Z") can't break it.
     */
    private LocalDate toLocalDate(XMLGregorianCalendar date) {
        return date == null ? null : LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }

    // ─────────────────────────────────────────────
    //  DTO MAPPERS
    // ─────────────────────────────────────────────

    private InvoiceResponseDTO toResponseDTO(Invoice invoice) {
        return InvoiceResponseDTO.builder()
                .id(invoice.getId())
                .externalInvoiceId(invoice.getExternalInvoiceId())
                .invoiceType(invoice.getInvoiceType())
                .issueDate(invoice.getIssueDate())
                .pointOfSale(invoice.getPointOfSale())
                .issuerCuit(invoice.getIssuerCuit())
                .issuerBusinessName(invoice.getIssuerBusinessName())
                .issuerAddress(invoice.getIssuerAddress())
                .receiverCuit(invoice.getReceiverCuit())
                .receiverBusinessName(invoice.getReceiverBusinessName())
                .receiverAddress(invoice.getReceiverAddress())
                .netAmount(invoice.getNetAmount())
                .vatAmount(invoice.getVatAmount())
                .totalAmount(invoice.getTotalAmount())
                .status(invoice.getStatus())
                .cae(invoice.getCae())
                .caeExpirationDate(invoice.getCaeExpirationDate())
                .afipInvoiceNumber(invoice.getAfipInvoiceNumber())
                .afipMessage(invoice.getAfipMessage())
                .createdAt(invoice.getCreatedAt())
                .items(invoice.getItems().stream()
                        .map(this::toItemResponseDTO)
                        .collect(Collectors.toList()))
                .build();
    }

    private InvoiceItemResponseDTO toItemResponseDTO(InvoiceItem item) {
        return InvoiceItemResponseDTO.builder()
                .id(item.getId())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .vatRate(item.getVatRate())
                .subtotal(item.getSubtotal())
                .build();
    }
}
