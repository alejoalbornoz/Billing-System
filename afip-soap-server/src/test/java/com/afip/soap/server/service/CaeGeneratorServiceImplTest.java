package com.afip.soap.server.service;


import com.afip.soap.server.model.AuthorizedInvoice;
import com.afip.soap.server.repository.AuthorizedInvoiceRepository;
import com.afip.soap.server.service.impl.CaeGeneratorServiceImpl;
import com.afip.soap.server.validator.InvoiceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaeGeneratorServiceImpl Tests")
class CaeGeneratorServiceImplTest {

    @Mock
    private InvoiceValidator invoiceValidator;

    @Mock
    private AuthorizedInvoiceRepository authorizedInvoiceRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private CaeGeneratorServiceImpl caeGeneratorService;

    @BeforeEach
    void setUp() {
        // Runs the callback directly, as if inside a transaction
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0)
                        .doInTransaction(mock(TransactionStatus.class)));

        // By default: a new invoice, first of its series
        lenient().when(authorizedInvoiceRepository.findByExternalInvoiceId(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(authorizedInvoiceRepository.findLastInvoiceNumber(anyString(), anyInt(), any()))
                .thenReturn(0L);
        lenient().when(authorizedInvoiceRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ─────────────────────────────────────────────
    //  APPROVED
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return APPROVED response when validation passes")
    void shouldReturnApproved_whenValidationPasses() {
        when(invoiceValidator.validate(any())).thenReturn(Collections.emptyList());

        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(buildRequest());

        assertThat(response.getStatus()).isEqualTo(server.soap.afip.invoice.AuthorizationStatus.APPROVED);
        assertThat(response.getCae()).isNotNull();
        assertThat(response.getCae()).hasSize(14);
        assertThat(response.getCaeExpirationDate()).isNotNull();
        assertThat(response.getAfipInvoiceNumber()).isNotNull();
        assertThat(response.getAfipInvoiceNumber()).isGreaterThan(0L);
        assertThat(response.getMessage()).isEqualTo("Invoice authorized successfully by AFIP.");
    }

    @Test
    @DisplayName("Should generate a 14-digit CAE on approval")
    void shouldGenerate14DigitCae_onApproval() {
        when(invoiceValidator.validate(any())).thenReturn(Collections.emptyList());

        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(buildRequest());

        assertThat(response.getCae()).matches("\\d{14}");
    }

    @Test
    @DisplayName("Should set CAE expiration date to 10 days from now")
    void shouldSetCaeExpirationDate_to10DaysFromNow() {
        when(invoiceValidator.validate(any())).thenReturn(Collections.emptyList());

        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(buildRequest());

        javax.xml.datatype.XMLGregorianCalendar expiration = response.getCaeExpirationDate();
        assertThat(LocalDate.of(expiration.getYear(), expiration.getMonth(), expiration.getDay()))
                .isEqualTo(LocalDate.now().plusDays(10));
    }

    @Test
    @DisplayName("Should echo externalInvoiceId in approved response")
    void shouldEchoExternalInvoiceId_inApprovedResponse() {
        when(invoiceValidator.validate(any())).thenReturn(Collections.emptyList());

        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildRequest();
        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(request);

        assertThat(response.getExternalInvoiceId())
                .isEqualTo(request.getExternalInvoiceId());
    }

    // ─────────────────────────────────────────────
    //  REJECTED
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return REJECTED response when validation fails")
    void shouldReturnRejected_whenValidationFails() {
        List<InvoiceValidator.ValidationError> errors = List.of(
                new InvoiceValidator.ValidationError("ERR_004", "Issuer CUIT format is invalid."),
                new InvoiceValidator.ValidationError("ERR_018", "Total amount does not match.")
        );
        when(invoiceValidator.validate(any())).thenReturn(errors);

        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(buildRequest());

        assertThat(response.getStatus()).isEqualTo(server.soap.afip.invoice.AuthorizationStatus.REJECTED);
        assertThat(response.getCae()).isNull();
        assertThat(response.getAfipInvoiceNumber()).isNull();
        assertThat(response.getErrors()).isNotNull();
        assertThat(response.getErrors().getError()).hasSize(2);
    }

    @Test
    @DisplayName("Should include all validation error codes in rejected response")
    void shouldIncludeAllErrorCodes_inRejectedResponse() {
        List<InvoiceValidator.ValidationError> errors = List.of(
                new InvoiceValidator.ValidationError("ERR_001", "External invoice ID is required."),
                new InvoiceValidator.ValidationError("ERR_010", "Invoice must contain at least one item.")
        );
        when(invoiceValidator.validate(any())).thenReturn(errors);

        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(buildRequest());

        assertThat(response.getErrors().getError())
                .extracting("code")
                .containsExactly("ERR_001", "ERR_010");
    }

    @Test
    @DisplayName("Should echo externalInvoiceId in rejected response")
    void shouldEchoExternalInvoiceId_inRejectedResponse() {
        when(invoiceValidator.validate(any())).thenReturn(
                List.of(new InvoiceValidator.ValidationError("ERR_001", "Required."))
        );

        server.soap.afip.invoice.AuthorizeInvoiceRequest request = buildRequest();
        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(request);

        assertThat(response.getExternalInvoiceId())
                .isEqualTo(request.getExternalInvoiceId());
    }

    // ─────────────────────────────────────────────
    //  INTERACTIONS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should call validator exactly once per request")
    void shouldCallValidator_exactlyOnce() {
        when(invoiceValidator.validate(any())).thenReturn(Collections.emptyList());

        caeGeneratorService.authorize(buildRequest());

        verify(invoiceValidator, times(1)).validate(any());
    }

    // ─────────────────────────────────────────────
    //  NUMBERING & IDEMPOTENCY
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should assign the next number of the series (last authorized + 1)")
    void shouldAssignNextNumberOfTheSeries() {
        when(invoiceValidator.validate(any())).thenReturn(Collections.emptyList());
        when(authorizedInvoiceRepository.findLastInvoiceNumber(
                "30-71234567-1", 1, server.soap.afip.invoice.InvoiceType.INVOICE_B))
                .thenReturn(41L);

        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(buildRequest());

        assertThat(response.getAfipInvoiceNumber()).isEqualTo(42L);
        verify(authorizedInvoiceRepository).save(any(AuthorizedInvoice.class));
    }

    @Test
    @DisplayName("Should return the original CAE when the invoice was already authorized")
    void shouldReturnOriginalCae_whenAlreadyAuthorized() {
        AuthorizedInvoice original = AuthorizedInvoice.builder()
                .externalInvoiceId("test-uuid-5678")
                .invoiceNumber(7L)
                .cae("12345678901234")
                .caeExpirationDate(LocalDate.of(2026, 6, 4))
                .authorizedAt(LocalDateTime.of(2026, 5, 25, 18, 0))
                .build();
        when(authorizedInvoiceRepository.findByExternalInvoiceId("test-uuid-5678"))
                .thenReturn(Optional.of(original));

        server.soap.afip.invoice.AuthorizeInvoiceResponse response = caeGeneratorService.authorize(buildRequest());

        assertThat(response.getStatus()).isEqualTo(server.soap.afip.invoice.AuthorizationStatus.APPROVED);
        assertThat(response.getCae()).isEqualTo("12345678901234");
        assertThat(response.getAfipInvoiceNumber()).isEqualTo(7L);
        assertThat(response.getMessage()).isEqualTo("Invoice already authorized. Returning the original CAE.");
        verifyNoInteractions(invoiceValidator);
        verify(authorizedInvoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not register anything when the invoice is rejected")
    void shouldNotRegisterInvoice_whenRejected() {
        when(invoiceValidator.validate(any())).thenReturn(
                List.of(new InvoiceValidator.ValidationError("ERR_022", "Issuer CUIT check digit is invalid."))
        );

        caeGeneratorService.authorize(buildRequest());

        verify(authorizedInvoiceRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    //  BUILDER
    // ─────────────────────────────────────────────

    private server.soap.afip.invoice.AuthorizeInvoiceRequest buildRequest() {
        server.soap.afip.invoice.AuthorizeInvoiceRequest request = new server.soap.afip.invoice.AuthorizeInvoiceRequest();
        request.setExternalInvoiceId("test-uuid-5678");
        request.setInvoiceType(server.soap.afip.invoice.InvoiceType.INVOICE_B);
        request.setPointOfSale(BigInteger.ONE);

        server.soap.afip.invoice.TaxpayerInfo issuer = new server.soap.afip.invoice.TaxpayerInfo();
        issuer.setCuit("30-71234567-1");
        issuer.setBusinessName("Mi Empresa SRL");
        request.setIssuer(issuer);

        server.soap.afip.invoice.TaxpayerInfo receiver = new server.soap.afip.invoice.TaxpayerInfo();
        receiver.setCuit("20-12345678-6");
        receiver.setBusinessName("Cliente SA");
        request.setReceiver(receiver);

        server.soap.afip.invoice.InvoiceItem item = new server.soap.afip.invoice.InvoiceItem();
        item.setDescription("Servicio");
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setVatRate(server.soap.afip.invoice.VatRate.VAT_21);
        item.setSubtotal(new BigDecimal("1000.00"));

        server.soap.afip.invoice.AuthorizeInvoiceRequest.Items items = new server.soap.afip.invoice.AuthorizeInvoiceRequest.Items();
        items.getItem().add(item);
        request.setItems(items);

        request.setNetAmount(new BigDecimal("1000.00"));
        request.setVatAmount(new BigDecimal("210.00"));
        request.setTotalAmount(new BigDecimal("1210.00"));

        return request;
    }
}