package com.billing_client.billing_client.service.impl;

import com.billing_client.billing_client.dto.request.InvoiceItemRequestDTO;
import com.billing_client.billing_client.dto.request.InvoiceRequestDTO;
import com.billing_client.billing_client.dto.response.InvoiceResponseDTO;
import com.billing_client.billing_client.exception.SoapCommunicationException;
import com.billing_client.billing_client.model.Invoice;
import com.billing_client.billing_client.model.enums.InvoiceStatus;
import com.billing_client.billing_client.model.enums.InvoiceType;
import com.billing_client.billing_client.model.enums.VatRate;
import com.billing_client.billing_client.repository.InvoiceRepository;
import com.billing_client.billing_client.soap.AfipSoapClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.soap.afip.invoice.AuthorizationStatus;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;

import javax.xml.datatype.DatatypeFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceServiceImpl Tests")
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private AfipSoapClient afipSoapClient;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    /** Status of the invoice at the moment of each save() call, in order. */
    private final List<InvoiceStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Behaves like the DB: returns the same instance and assigns an ID on the first save
        lenient().when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            savedStatuses.add(invoice.getStatus());
            if (invoice.getId() == null) {
                invoice.setId(1L);
            }
            return invoice;
        });
    }

    // ─────────────────────────────────────────────
    //  AMOUNTS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should calculate subtotals and amounts from the items before calling AFIP")
    void shouldCalculateAmountsFromItems() throws Exception {
        when(afipSoapClient.authorize(any())).thenReturn(approvedAfipResponse());

        InvoiceResponseDTO response = invoiceService.processInvoice(buildRequest());

        ArgumentCaptor<Invoice> sentToAfip = ArgumentCaptor.forClass(Invoice.class);
        verify(afipSoapClient).authorize(sentToAfip.capture());

        Invoice invoice = sentToAfip.getValue();
        assertThat(invoice.getItems()).extracting(item -> item.getSubtotal().toPlainString())
                .containsExactly("200.00", "50.00");
        assertThat(invoice.getNetAmount()).isEqualByComparingTo("250.00");
        assertThat(invoice.getVatAmount()).isEqualByComparingTo("47.25");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("297.25");

        assertThat(response.getTotalAmount()).isEqualByComparingTo("297.25");
    }

    // ─────────────────────────────────────────────
    //  FLOW
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should save the invoice as PENDING before calling AFIP")
    void shouldSaveAsPending_beforeCallingAfip() throws Exception {
        when(afipSoapClient.authorize(any())).thenReturn(approvedAfipResponse());

        invoiceService.processInvoice(buildRequest());

        InOrder inOrder = inOrder(invoiceRepository, afipSoapClient);
        inOrder.verify(invoiceRepository).save(any());
        inOrder.verify(afipSoapClient).authorize(any());
        inOrder.verify(invoiceRepository).save(any());

        assertThat(savedStatuses).containsExactly(InvoiceStatus.PENDING, InvoiceStatus.APPROVED);
    }

    @Test
    @DisplayName("Should store CAE data when AFIP approves the invoice")
    void shouldStoreCae_whenAfipApproves() throws Exception {
        when(afipSoapClient.authorize(any())).thenReturn(approvedAfipResponse());

        InvoiceResponseDTO response = invoiceService.processInvoice(buildRequest());

        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(response.getCae()).isEqualTo("43969379396831");
        assertThat(response.getCaeExpirationDate()).isEqualTo(LocalDate.of(2026, 6, 4));
        assertThat(response.getAfipInvoiceNumber()).isEqualTo(2799044L);
    }

    @Test
    @DisplayName("Should include AFIP error codes in the message when the invoice is rejected")
    void shouldIncludeErrorCodes_whenAfipRejects() {
        when(afipSoapClient.authorize(any())).thenReturn(rejectedAfipResponse());

        InvoiceResponseDTO response = invoiceService.processInvoice(buildRequest());

        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.REJECTED);
        assertThat(response.getCae()).isNull();
        assertThat(response.getAfipMessage())
                .contains("Invoice rejected.")
                .contains("ERR_004: Issuer CUIT format is invalid.");
    }

    @Test
    @DisplayName("Should keep the invoice PENDING when AFIP cannot be reached")
    void shouldKeepPending_whenAfipIsUnreachable() {
        when(afipSoapClient.authorize(any()))
                .thenThrow(new SoapCommunicationException("Connection refused"));

        InvoiceResponseDTO response = invoiceService.processInvoice(buildRequest());

        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(response.getAfipMessage()).isEqualTo(InvoiceServiceImpl.AFIP_UNAVAILABLE_MESSAGE);
        assertThat(savedStatuses).containsExactly(InvoiceStatus.PENDING, InvoiceStatus.PENDING);
    }

    // ─────────────────────────────────────────────
    //  RETRY
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should resend every pending invoice and store the AFIP result")
    void shouldResendPendingInvoices() throws Exception {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);
        Invoice first = pendingInvoice(10L);
        Invoice second = pendingInvoice(11L);

        when(invoiceRepository.findByStatusAndCreatedAtBefore(InvoiceStatus.PENDING, cutoff))
                .thenReturn(List.of(first, second));
        when(afipSoapClient.authorize(any())).thenReturn(approvedAfipResponse());

        int resent = invoiceService.resendPendingInvoices(cutoff);

        assertThat(resent).isEqualTo(2);
        verify(afipSoapClient, times(2)).authorize(any());
        assertThat(first.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(second.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }

    @Test
    @DisplayName("Should not call AFIP when there are no pending invoices")
    void shouldNotCallAfip_whenNoPendingInvoices() {
        when(invoiceRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        int resent = invoiceService.resendPendingInvoices(LocalDateTime.now());

        assertThat(resent).isZero();
        verifyNoInteractions(afipSoapClient);
    }

    // ─────────────────────────────────────────────
    //  BUILDERS
    // ─────────────────────────────────────────────

    private InvoiceRequestDTO buildRequest() {
        return InvoiceRequestDTO.builder()
                .invoiceType(InvoiceType.INVOICE_B)
                .issueDate(LocalDate.of(2026, 5, 19))
                .pointOfSale(1)
                .issuerCuit("30-71234567-0")
                .issuerBusinessName("Mi Empresa SRL")
                .receiverCuit("20-12345678-9")
                .receiverBusinessName("Cliente SA")
                .items(List.of(
                        InvoiceItemRequestDTO.builder()
                                .description("Horas de desarrollo")
                                .quantity(new BigDecimal("2"))
                                .unitPrice(new BigDecimal("100.00"))
                                .vatRate(VatRate.VAT_21)
                                .build(),
                        InvoiceItemRequestDTO.builder()
                                .description("Libro técnico")
                                .quantity(new BigDecimal("1"))
                                .unitPrice(new BigDecimal("50.00"))
                                .vatRate(VatRate.VAT_10_5)
                                .build()
                ))
                .build();
    }

    private Invoice pendingInvoice(long id) {
        return Invoice.builder()
                .id(id)
                .externalInvoiceId("external-" + id)
                .status(InvoiceStatus.PENDING)
                .build();
    }

    private AuthorizeInvoiceResponse approvedAfipResponse() throws Exception {
        AuthorizeInvoiceResponse response = new AuthorizeInvoiceResponse();
        response.setStatus(AuthorizationStatus.APPROVED);
        response.setCae("43969379396831");
        response.setCaeExpirationDate(
                DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-06-04"));
        response.setAfipInvoiceNumber(2799044L);
        response.setMessage("Invoice authorized successfully by AFIP.");
        return response;
    }

    private AuthorizeInvoiceResponse rejectedAfipResponse() {
        AuthorizeInvoiceResponse.Errors.Error error = new AuthorizeInvoiceResponse.Errors.Error();
        error.setCode("ERR_004");
        error.setDescription("Issuer CUIT format is invalid.");

        AuthorizeInvoiceResponse.Errors errors = new AuthorizeInvoiceResponse.Errors();
        errors.getError().add(error);

        AuthorizeInvoiceResponse response = new AuthorizeInvoiceResponse();
        response.setStatus(AuthorizationStatus.REJECTED);
        response.setMessage("Invoice rejected. Please review the errors.");
        response.setErrors(errors);
        return response;
    }
}
