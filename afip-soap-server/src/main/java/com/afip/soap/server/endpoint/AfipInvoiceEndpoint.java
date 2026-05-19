package com.afip.soap.server.endpoint;


import com.afip.soap.server.service.CaeGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;

@Slf4j
@Endpoint                   // Marks this class as a SOAP endpoint (like @RestController but for SOAP)
@RequiredArgsConstructor
public class AfipInvoiceEndpoint {

    // Namespace must match exactly the targetNamespace defined in afip-invoice.xsd
    private static final String NAMESPACE_URI = "http://www.afip.soap.server/invoice";

    private final CaeGeneratorService caeGeneratorService;

    /**
     * Handles incoming SOAP requests for invoice authorization.
     *
     * How it works:
     *   - @PayloadRoot tells Spring: "when a SOAP message arrives with this namespace
     *     and this localPart (the XML root element name), call this method"
     *
     *   - @RequestPayload tells Spring: "deserialize the XML body into this Java object"
     *     (Spring uses the JAXB classes we generated from the XSD)
     *
     *   - @ResponsePayload tells Spring: "serialize the returned Java object back to XML"
     *
     * REST equivalent:
     *   @PostMapping("/invoices")
     *   public ResponseEntity<InvoiceResponseDTO> create(@RequestBody InvoiceRequestDTO dto)
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "AuthorizeInvoiceRequest")
    @ResponsePayload
    public AuthorizeInvoiceResponse authorizeInvoice(
            @RequestPayload AuthorizeInvoiceRequest request) {

        log.info("SOAP request received — externalInvoiceId: {} | issuer CUIT: {}",
                request.getExternalInvoiceId(),
                request.getIssuer().getCuit());

        AuthorizeInvoiceResponse response = caeGeneratorService.authorize(request);

        log.info("SOAP response sent — externalInvoiceId: {} | status: {}",
                response.getExternalInvoiceId(),
                response.getStatus());

        return response;
    }
}