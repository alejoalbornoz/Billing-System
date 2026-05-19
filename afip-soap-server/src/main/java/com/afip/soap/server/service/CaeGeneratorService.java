package com.afip.soap.server.service;

import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;

public interface CaeGeneratorService {

    /**
     * Receives an invoice authorization request and returns the AFIP response.
     *
     * Responsibilities:
     *   1. Validate the invoice data (CUIT format, amounts, required fields)
     *   2. Generate a CAE code if validation passes
     *   3. Build and return the response with APPROVED or REJECTED status
     *
     * @param request the invoice data sent by the billing client via SOAP
     * @return the authorization response with CAE or rejection errors
     */
    AuthorizeInvoiceResponse authorize(AuthorizeInvoiceRequest request);
}
