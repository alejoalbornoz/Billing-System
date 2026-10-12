package com.billing_client.billing_client.soap;

import com.billing_client.billing_client.exception.SoapCommunicationException;
import com.billing_client.billing_client.model.Invoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import server.soap.afip.invoice.*;


import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AfipSoapClient {

    private final WebServiceTemplate webServiceTemplate;

    @Value("${afip.soap.url}")
    private String afipSoapUrl;

    /**
     * Sends an invoice authorization request to the AFIP SOAP server
     * and returns the response.
     *
     * Steps:
     *   1. Build the AuthorizeInvoiceRequest XML object from the saved invoice
     *   2. Send it via WebServiceTemplate (handles the SOAP envelope automatically)
     *   3. Cast and return the response
     *
     * The request is built from the persisted entity (not the REST DTO), so what
     * AFIP authorizes is exactly what is stored — including the calculated amounts.
     *
     * @param invoice the saved invoice, with its items and calculated amounts
     * @return        the AFIP authorization response with CAE or errors
     */
    public AuthorizeInvoiceResponse authorize(Invoice invoice) {
        String externalInvoiceId = invoice.getExternalInvoiceId();
        log.info("Sending SOAP request to AFIP — externalInvoiceId: {}", externalInvoiceId);

        try {
            AuthorizeInvoiceRequest soapRequest = buildSoapRequest(invoice);

            // WebServiceTemplate sends the request and returns the response
            // It wraps the object in a SOAP Envelope automatically
            AuthorizeInvoiceResponse response = (AuthorizeInvoiceResponse)
                    webServiceTemplate.marshalSendAndReceive(
                            afipSoapUrl,
                            soapRequest,
                            new SoapActionCallback("")
                    );

            log.info("SOAP response received — externalInvoiceId: {} | status: {}",
                    externalInvoiceId, response.getStatus());

            return response;

        } catch (Exception e) {
            log.error("SOAP communication error — externalInvoiceId: {} | error: {}",
                    externalInvoiceId, e.getMessage());
            throw new SoapCommunicationException(
                    "Failed to communicate with AFIP SOAP server: " + e.getMessage(), e
            );
        }
    }

    // ─────────────────────────────────────────────
    //  REQUEST BUILDER
    // ─────────────────────────────────────────────

    /**
     * Converts our Invoice entity into the JAXB-generated
     * AuthorizeInvoiceRequest that Spring-WS can serialize to XML.
     */
    private AuthorizeInvoiceRequest buildSoapRequest(Invoice invoice) {
        AuthorizeInvoiceRequest soapRequest = new AuthorizeInvoiceRequest();

        soapRequest.setExternalInvoiceId(invoice.getExternalInvoiceId());
        soapRequest.setInvoiceType(
                server.soap.afip.invoice.InvoiceType.valueOf(invoice.getInvoiceType().name())
        );
        soapRequest.setIssueDate(toXmlDate(invoice.getIssueDate()));

        if (invoice.getPointOfSale() != null) {
            soapRequest.setPointOfSale(java.math.BigInteger.valueOf(invoice.getPointOfSale()));
        }

        // Issuer
        TaxpayerInfo issuer = new TaxpayerInfo();
        issuer.setCuit(invoice.getIssuerCuit());
        issuer.setBusinessName(invoice.getIssuerBusinessName());
        issuer.setAddress(invoice.getIssuerAddress());
        soapRequest.setIssuer(issuer);

        // Receiver
        TaxpayerInfo receiver = new TaxpayerInfo();
        receiver.setCuit(invoice.getReceiverCuit());
        receiver.setBusinessName(invoice.getReceiverBusinessName());
        receiver.setAddress(invoice.getReceiverAddress());
        soapRequest.setReceiver(receiver);

        // Items
        AuthorizeInvoiceRequest.Items soapItems = new AuthorizeInvoiceRequest.Items();
        for (com.billing_client.billing_client.model.InvoiceItem item : invoice.getItems()) {
            InvoiceItem soapItem = new InvoiceItem();
            soapItem.setDescription(item.getDescription());
            soapItem.setQuantity(item.getQuantity());
            soapItem.setUnitPrice(item.getUnitPrice());
            soapItem.setVatRate(VatRate.valueOf(item.getVatRate().name()));
            soapItem.setSubtotal(item.getSubtotal());
            soapItems.getItem().add(soapItem);
        }
        soapRequest.setItems(soapItems);

        // Amounts
        soapRequest.setNetAmount(invoice.getNetAmount());
        soapRequest.setVatAmount(invoice.getVatAmount());
        soapRequest.setTotalAmount(invoice.getTotalAmount());

        return soapRequest;
    }

    // ─────────────────────────────────────────────
    //  HELPER METHODS
    // ─────────────────────────────────────────────

    /**
     * Converts LocalDate to XMLGregorianCalendar (required by JAXB xs:date)
     */
    private XMLGregorianCalendar toXmlDate(LocalDate date) {
        try {
            return DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(date.toString());
        } catch (Exception e) {
            throw new RuntimeException("Error converting date: " + date, e);
        }
    }
}
