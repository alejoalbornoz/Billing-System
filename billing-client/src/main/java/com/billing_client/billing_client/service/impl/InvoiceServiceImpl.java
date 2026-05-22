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
import server.soap.afip.invoice.AuthorizationStatus;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final AfipSoapClient afipSoapClient;

    // ─────────────────────────────────────────────
    //  MAIN FLOW
    // ─────────────────────────────────────────────

    @Override
    @Transactional
    public InvoiceResponseDTO processInvoice(InvoiceRequestDTO request) {

        // Step 1 — generate a unique external ID for AFIP correlation
        String externalInvoiceId = UUID.randomUUID().toString();
        log.info("Processing new invoice — externalInvoiceId: {}", externalInvoiceId);

        // Step 2 — build and save the invoice as PENDING before calling AFIP
        // This way we have a record even if AFIP call fails
        Invoice invoice = buildInvoice(externalInvoiceId, request);
        invoice = invoiceRepository.save(invoice);
        log.info("Invoice saved as PENDING — id: {}", invoice.getId());

        // Step 3 — call AFIP SOAP server
        try {
            AuthorizeInvoiceResponse afipResponse =
                    afipSoapClient.authorize(externalInvoiceId, request);

            // Step 4 — update invoice with AFIP response
            updateInvoiceWithAfipResponse(invoice, afipResponse);
            invoice = invoiceRepository.save(invoice);

            log.info("Invoice updated — id: {} | status: {}", invoice.getId(), invoice.getStatus());

        } catch (SoapCommunicationException e) {
            // If SOAP call fails, mark as REJECTED and save the error
            log.error("AFIP SOAP call failed — id: {} | error: {}", invoice.getId(), e.getMessage());
            invoice.setStatus(InvoiceStatus.REJECTED);
            invoice.setAfipMessage("SOAP communication error: " + e.getMessage());
            invoiceRepository.save(invoice);
        }

        // Step 5 — return the final invoice as DTO
        return toResponseDTO(invoice);
    }

    @Override
    public InvoiceResponseDTO findById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found with id: " + id));
        return toResponseDTO(invoice);
    }

    @Override
    public InvoiceResponseDTO findByExternalInvoiceId(String externalInvoiceId) {
        Invoice invoice = invoiceRepository.findByExternalInvoiceId(externalInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Invoice not found with externalInvoiceId: " + externalInvoiceId));
        return toResponseDTO(invoice);
    }

    @Override
    public List<InvoiceResponseDTO> findAll() {
        return invoiceRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    //  BUILDERS
    // ─────────────────────────────────────────────

    /**
     * Builds an Invoice entity from the request DTO.
     * Status starts as PENDING — set by @PrePersist in the entity.
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
                .netAmount(request.getNetAmount())
                .vatAmount(request.getVatAmount())
                .totalAmount(request.getTotalAmount())
                .status(InvoiceStatus.PENDING)
                .build();

        // Build and link each item to the invoice
        List<InvoiceItem> items = request.getItems().stream()
                .map(itemDTO -> buildInvoiceItem(itemDTO, invoice))
                .collect(Collectors.toList());

        invoice.setItems(items);

        return invoice;
    }

    private InvoiceItem buildInvoiceItem(InvoiceItemRequestDTO dto, Invoice invoice) {
        BigDecimal subtotal = dto.getQuantity().multiply(dto.getUnitPrice());

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
     * Updates the invoice fields based on what AFIP returned.
     */
    private void updateInvoiceWithAfipResponse(Invoice invoice,
                                               AuthorizeInvoiceResponse afipResponse) {
        if (afipResponse.getStatus() == AuthorizationStatus.APPROVED) {
            invoice.setStatus(InvoiceStatus.APPROVED);
            invoice.setCae(afipResponse.getCae());
            invoice.setCaeExpirationDate(
                    LocalDate.parse(afipResponse.getCaeExpirationDate().toString())
            );
            invoice.setAfipInvoiceNumber(afipResponse.getAfipInvoiceNumber());
        } else {
            invoice.setStatus(InvoiceStatus.REJECTED);
        }

        invoice.setAfipMessage(afipResponse.getMessage());
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
