package com.afip.soap.server.service.impl;


import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import com.afip.soap.server.service.CaeGeneratorService;
import com.afip.soap.server.validator.InvoiceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import server.soap.afip.invoice.AuthorizationStatus;
import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaeGeneratorServiceImpl implements CaeGeneratorService {

    private final InvoiceValidator invoiceValidator;

    @Override
    public AuthorizeInvoiceResponse authorize(AuthorizeInvoiceRequest request) {

        // Step 1 — validate the incoming invoice data
        List<InvoiceValidator.ValidationError> errors = invoiceValidator.validate(request);

        // Step 2 — if there are errors, return a REJECTED response
        if (!errors.isEmpty()) {
            log.warn("Invoice rejected — externalInvoiceId: {} | errors: {}",
                    request.getExternalInvoiceId(), errors.size());
            return buildRejectedResponse(request, errors);
        }

        // Step 3 — all validations passed, generate the CAE
        String cae = generateCae();
        long afipInvoiceNumber = generateAfipInvoiceNumber();

        log.info("Invoice approved — externalInvoiceId: {} | CAE: {}",
                request.getExternalInvoiceId(), cae);

        return buildApprovedResponse(request, cae, afipInvoiceNumber);
    }

    // ─────────────────────────────────────────────
    //  CAE GENERATION
    // ─────────────────────────────────────────────

    /**
     * Generates a fictional CAE code.
     *
     * Real AFIP CAE format: 14 numeric digits.
     * Example: 75123456789012
     */
    private String generateCae() {
        long randomNumber = ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L);
        return String.valueOf(randomNumber);
    }

    /**
     * Generates a fictional AFIP invoice number.
     * In production this would be a sequential number assigned by AFIP.
     */
    private long generateAfipInvoiceNumber() {
        return ThreadLocalRandom.current().nextLong(1_000_000L, 9_999_999L);
    }

    // ─────────────────────────────────────────────
    //  RESPONSE BUILDERS
    // ─────────────────────────────────────────────

    private AuthorizeInvoiceResponse buildApprovedResponse(
            AuthorizeInvoiceRequest request,
            String cae,
            long afipInvoiceNumber) {

        AuthorizeInvoiceResponse response = new AuthorizeInvoiceResponse();

        response.setExternalInvoiceId(request.getExternalInvoiceId());
        response.setStatus(AuthorizationStatus.APPROVED);
        response.setCae(cae);
        response.setCaeExpirationDate(toXmlDate(LocalDate.now().plusDays(10)));
        response.setAfipInvoiceNumber(afipInvoiceNumber);
        response.setMessage("Invoice authorized successfully by AFIP.");
        response.setProcessedAt(toXmlDateTime(LocalDateTime.now()));

        return response;
    }

    private AuthorizeInvoiceResponse buildRejectedResponse(
            AuthorizeInvoiceRequest request,
            List<InvoiceValidator.ValidationError> validationErrors) {

        AuthorizeInvoiceResponse response = new AuthorizeInvoiceResponse();

        response.setExternalInvoiceId(request.getExternalInvoiceId());
        response.setStatus(AuthorizationStatus.REJECTED);
        response.setMessage("Invoice rejected. Please review the errors.");
        response.setProcessedAt(toXmlDateTime(LocalDateTime.now()));

        AuthorizeInvoiceResponse.Errors soapErrors = new AuthorizeInvoiceResponse.Errors();

        validationErrors.forEach(validationError -> {
            AuthorizeInvoiceResponse.Errors.Error soapError =
                    new AuthorizeInvoiceResponse.Errors.Error();
            soapError.setCode(validationError.code());
            soapError.setDescription(validationError.description());
            soapErrors.getError().add(soapError);
        });

        response.setErrors(soapErrors);

        return response;
    }

    // ─────────────────────────────────────────────
//  XML DATE CONVERTERS
// ─────────────────────────────────────────────

    /**
     * Converts a LocalDate to XMLGregorianCalendar (xs:date)
     * Example: 2026-05-29
     */
    private XMLGregorianCalendar toXmlDate(LocalDate date) {
        try {
            return DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(date.toString());
        } catch (Exception e) {
            throw new RuntimeException("Error converting date: " + date, e);
        }
    }

    /**
     * Converts a LocalDateTime to XMLGregorianCalendar (xs:dateTime)
     * Example: 2026-05-19T16:30:00
     */
    private XMLGregorianCalendar toXmlDateTime(LocalDateTime dateTime) {
        try {
            return DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(
                            dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    );
        } catch (Exception e) {
            throw new RuntimeException("Error converting dateTime: " + dateTime, e);
        }
    }
}