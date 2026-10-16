package com.afip.soap.server.service;

import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;
import server.soap.afip.invoice.GetLastAuthorizedInvoiceRequest;
import server.soap.afip.invoice.GetLastAuthorizedInvoiceResponse;

public interface CaeGeneratorService {

    /**
     * Receives an invoice authorization request and returns the AFIP response.
     *
     * Responsibilities:
     *   1. If the externalInvoiceId was already authorized, return the original CAE
     *   2. Validate the invoice data (CUIT, point of sale, amounts, required fields)
     *   3. Assign the next correlative number of the series and generate a CAE
     *   4. Build and return the response with APPROVED or REJECTED status
     *
     * @param request the invoice data sent by the billing client via SOAP
     * @return the authorization response with CAE or rejection errors
     */
    AuthorizeInvoiceResponse authorize(AuthorizeInvoiceRequest request);

    /**
     * Returns the last number authorized for a series (issuer CUIT + point of sale + type),
     * or 0 if none — equivalent to AFIP's FECompUltimoAutorizado.
     */
    GetLastAuthorizedInvoiceResponse getLastAuthorizedInvoice(GetLastAuthorizedInvoiceRequest request);
}
