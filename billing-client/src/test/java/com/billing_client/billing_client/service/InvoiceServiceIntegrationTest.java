package com.billing_client.billing_client.service;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Runs the invoice flow against a real PostgreSQL (Testcontainers), with AFIP mocked.
 *
 * Covers what the unit tests can't: the PENDING invoice is really committed,
 * and the retry query loads the items outside a web request (no lazy loading there).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("InvoiceService Integration Tests")
class InvoiceServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private AfipSoapClient afipSoapClient;

    @BeforeEach
    void cleanDatabase() {
        invoiceRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist a PENDING invoice with calculated amounts when AFIP is unreachable")
    void shouldPersistPendingInvoice_whenAfipIsUnreachable() {
        when(afipSoapClient.authorize(any()))
                .thenThrow(new SoapCommunicationException("Connection refused"));

        InvoiceResponseDTO response = invoiceService.processInvoice(buildRequest());

        Invoice stored = invoiceRepository.findById(response.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(stored.getNetAmount()).isEqualByComparingTo("250.00");
        assertThat(stored.getVatAmount()).isEqualByComparingTo("47.25");
        assertThat(stored.getTotalAmount()).isEqualByComparingTo("297.25");
    }

    @Test
    @DisplayName("Should resend a PENDING invoice with its items and store the CAE")
    void shouldResendPendingInvoice_withItsItems() {
        when(afipSoapClient.authorize(any()))
                .thenThrow(new SoapCommunicationException("Connection refused"));
        Long id = invoiceService.processInvoice(buildRequest()).getId();

        // AFIP is back. Record how many items reach the SOAP client:
        // reading them here would fail if the retry query didn't fetch them.
        List<Integer> itemsSentToAfip = new ArrayList<>();
        reset(afipSoapClient);
        when(afipSoapClient.authorize(any())).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            itemsSentToAfip.add(invoice.getItems().size());
            return approvedAfipResponse();
        });

        int resent = invoiceService.resendPendingInvoices(LocalDateTime.now().plusMinutes(1));

        assertThat(resent).isEqualTo(1);
        assertThat(itemsSentToAfip).containsExactly(2);

        Invoice stored = invoiceRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(stored.getCae()).isEqualTo("43969379396831");
    }

    // ─────────────────────────────────────────────
    //  BUILDERS
    // ─────────────────────────────────────────────

    private InvoiceRequestDTO buildRequest() {
        return InvoiceRequestDTO.builder()
                .invoiceType(InvoiceType.INVOICE_B)
                .issueDate(LocalDate.of(2026, 5, 19))
                .pointOfSale(1)
                .issuerCuit("30-71234567-1")
                .issuerBusinessName("Mi Empresa SRL")
                .receiverCuit("20-12345678-6")
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
}
