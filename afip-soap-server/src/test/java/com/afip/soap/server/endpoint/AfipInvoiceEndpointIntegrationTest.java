package com.afip.soap.server.endpoint;

import com.afip.soap.server.repository.AuthorizedInvoiceRepository;
import com.afip.soap.server.service.CaeGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.InvoiceItem;
import server.soap.afip.invoice.InvoiceType;
import server.soap.afip.invoice.TaxpayerInfo;
import server.soap.afip.invoice.VatRate;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.transform.Source;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/**
 * Sends real SOAP messages to the endpoint, backed by an in-memory H2 registry.
 */
@SpringBootTest
@AutoConfigureTestDatabase
@DisplayName("AfipInvoiceEndpoint Integration Tests")
class AfipInvoiceEndpointIntegrationTest {

    private static final Map<String, String> NS = Map.of("ns", "http://www.afip.soap.server/invoice");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuthorizedInvoiceRepository authorizedInvoiceRepository;

    @Autowired
    private CaeGeneratorService caeGeneratorService;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(applicationContext);
        authorizedInvoiceRepository.deleteAll();
    }

    // ─────────────────────────────────────────────
    //  NUMBERING
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should number invoices correlatively and independently per series")
    void shouldNumberInvoicesCorrelativelyPerSeries() {
        expectNumber(authorizeRequest("ext-1", 1, "INVOICE_B"), 1);
        expectNumber(authorizeRequest("ext-2", 1, "INVOICE_B"), 2);
        expectNumber(authorizeRequest("ext-3", 1, "INVOICE_B"), 3);

        // Another invoice type or point of sale is a different series
        expectNumber(authorizeRequest("ext-4", 1, "INVOICE_A"), 1);
        expectNumber(authorizeRequest("ext-5", 2, "INVOICE_B"), 1);
    }

    @Test
    @DisplayName("Should not consume a number when the invoice is rejected")
    void shouldNotConsumeNumber_whenInvoiceIsRejected() {
        expectNumber(authorizeRequest("ext-1", 1, "INVOICE_B"), 1);

        client.sendRequest(withPayload(authorizeRequest("ext-bad", 1, "INVOICE_B", "30-71234567-0")))
                .andExpect(noFault())
                .andExpect(xpath("//ns:status", NS).evaluatesTo("REJECTED"))
                .andExpect(xpath("//ns:error/ns:code", NS).evaluatesTo("ERR_022"));

        expectNumber(authorizeRequest("ext-2", 1, "INVOICE_B"), 2);
    }

    // ─────────────────────────────────────────────
    //  IDEMPOTENCY
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return the original CAE and number when the same invoice is sent twice")
    void shouldReturnOriginalCae_whenSameInvoiceIsSentTwice() {
        expectNumber(authorizeRequest("ext-1", 1, "INVOICE_B"), 1);
        String originalCae = authorizedInvoiceRepository.findByExternalInvoiceId("ext-1")
                .orElseThrow()
                .getCae();

        client.sendRequest(withPayload(authorizeRequest("ext-1", 1, "INVOICE_B")))
                .andExpect(noFault())
                .andExpect(xpath("//ns:status", NS).evaluatesTo("APPROVED"))
                .andExpect(xpath("//ns:cae", NS).evaluatesTo(originalCae))
                .andExpect(xpath("//ns:afipInvoiceNumber", NS).evaluatesTo(1));

        assertThat(authorizedInvoiceRepository.count()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────
    //  LAST AUTHORIZED INVOICE
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return the last authorized number of a series, or 0 if empty")
    void shouldReturnLastAuthorizedNumber() {
        expectNumber(authorizeRequest("ext-1", 1, "INVOICE_B"), 1);
        expectNumber(authorizeRequest("ext-2", 1, "INVOICE_B"), 2);

        client.sendRequest(withPayload(lastAuthorizedRequest(1, "INVOICE_B")))
                .andExpect(noFault())
                .andExpect(xpath("//ns:lastInvoiceNumber", NS).evaluatesTo(2));

        client.sendRequest(withPayload(lastAuthorizedRequest(1, "INVOICE_A")))
                .andExpect(noFault())
                .andExpect(xpath("//ns:lastInvoiceNumber", NS).evaluatesTo(0));
    }

    // ─────────────────────────────────────────────
    //  CONCURRENCY
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should never assign the same number to concurrent requests of the same series")
    void shouldAssignUniqueNumbers_underConcurrentRequests() throws Exception {
        int requests = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            List<Callable<Long>> tasks = IntStream.rangeClosed(1, requests)
                    .mapToObj(i -> (Callable<Long>) () -> caeGeneratorService
                            .authorize(javaRequest("concurrent-" + i))
                            .getAfipInvoiceNumber())
                    .toList();

            List<Long> numbers = new java.util.ArrayList<>();
            for (Future<Long> future : pool.invokeAll(tasks)) {
                numbers.add(future.get());
            }

            assertThat(numbers).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, requests).boxed().toList());
        } finally {
            pool.shutdownNow();
        }
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    private void expectNumber(Source request, int expectedNumber) {
        client.sendRequest(withPayload(request))
                .andExpect(noFault())
                .andExpect(xpath("//ns:status", NS).evaluatesTo("APPROVED"))
                .andExpect(xpath("//ns:afipInvoiceNumber", NS).evaluatesTo(expectedNumber));
    }

    private Source authorizeRequest(String externalInvoiceId, int pointOfSale, String invoiceType) {
        return authorizeRequest(externalInvoiceId, pointOfSale, invoiceType, "30-71234567-1");
    }

    private Source authorizeRequest(String externalInvoiceId, int pointOfSale,
                                    String invoiceType, String issuerCuit) {
        return new StringSource("""
                <AuthorizeInvoiceRequest xmlns="http://www.afip.soap.server/invoice">
                    <externalInvoiceId>%s</externalInvoiceId>
                    <invoiceType>%s</invoiceType>
                    <issueDate>2026-10-16</issueDate>
                    <issuer>
                        <cuit>%s</cuit>
                        <businessName>Mi Empresa SRL</businessName>
                    </issuer>
                    <receiver>
                        <cuit>20-12345678-6</cuit>
                        <businessName>Cliente SA</businessName>
                    </receiver>
                    <items>
                        <item>
                            <description>Servicio</description>
                            <quantity>1</quantity>
                            <unitPrice>1000.00</unitPrice>
                            <vatRate>VAT_21</vatRate>
                            <subtotal>1000.00</subtotal>
                        </item>
                    </items>
                    <netAmount>1000.00</netAmount>
                    <vatAmount>210.00</vatAmount>
                    <totalAmount>1210.00</totalAmount>
                    <pointOfSale>%d</pointOfSale>
                </AuthorizeInvoiceRequest>
                """.formatted(externalInvoiceId, invoiceType, issuerCuit, pointOfSale));
    }

    private Source lastAuthorizedRequest(int pointOfSale, String invoiceType) {
        return new StringSource("""
                <GetLastAuthorizedInvoiceRequest xmlns="http://www.afip.soap.server/invoice">
                    <issuerCuit>30-71234567-1</issuerCuit>
                    <pointOfSale>%d</pointOfSale>
                    <invoiceType>%s</invoiceType>
                </GetLastAuthorizedInvoiceRequest>
                """.formatted(pointOfSale, invoiceType));
    }

    private AuthorizeInvoiceRequest javaRequest(String externalInvoiceId) throws Exception {
        AuthorizeInvoiceRequest request = new AuthorizeInvoiceRequest();
        request.setExternalInvoiceId(externalInvoiceId);
        request.setInvoiceType(InvoiceType.INVOICE_B);
        request.setIssueDate(DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-10-16"));
        request.setPointOfSale(BigInteger.ONE);

        TaxpayerInfo issuer = new TaxpayerInfo();
        issuer.setCuit("30-71234567-1");
        issuer.setBusinessName("Mi Empresa SRL");
        request.setIssuer(issuer);

        TaxpayerInfo receiver = new TaxpayerInfo();
        receiver.setCuit("20-12345678-6");
        receiver.setBusinessName("Cliente SA");
        request.setReceiver(receiver);

        InvoiceItem item = new InvoiceItem();
        item.setDescription("Servicio");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setVatRate(VatRate.VAT_21);
        item.setSubtotal(new BigDecimal("1000.00"));

        AuthorizeInvoiceRequest.Items items = new AuthorizeInvoiceRequest.Items();
        items.getItem().add(item);
        request.setItems(items);

        request.setNetAmount(new BigDecimal("1000.00"));
        request.setVatAmount(new BigDecimal("210.00"));
        request.setTotalAmount(new BigDecimal("1210.00"));

        return request;
    }
}
