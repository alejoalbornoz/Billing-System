package com.afip.soap.server.service.impl;


import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import com.afip.soap.server.model.AuthorizedInvoice;
import com.afip.soap.server.repository.AuthorizedInvoiceRepository;
import com.afip.soap.server.service.CaeGeneratorService;
import com.afip.soap.server.validator.InvoiceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import server.soap.afip.invoice.AuthorizationStatus;
import server.soap.afip.invoice.AuthorizeInvoiceRequest;
import server.soap.afip.invoice.AuthorizeInvoiceResponse;
import server.soap.afip.invoice.GetLastAuthorizedInvoiceRequest;
import server.soap.afip.invoice.GetLastAuthorizedInvoiceResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaeGeneratorServiceImpl implements CaeGeneratorService {

    static final String APPROVED_MESSAGE = "Invoice authorized successfully by AFIP.";
    static final String ALREADY_AUTHORIZED_MESSAGE =
            "Invoice already authorized. Returning the original CAE.";

    private static final int CAE_VALIDITY_DAYS = 10;

    private final InvoiceValidator invoiceValidator;
    private final AuthorizedInvoiceRepository authorizedInvoiceRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Synchronized so that reading the last number, saving the next one and
     * committing happen as a single step: two requests never get the same number.
     *
     * The lock wraps the whole transaction on purpose. With @Transactional the
     * commit would happen after the lock is released, and a second request could
     * still read the previous last number.
     *
     * A JVM lock is enough because the mock runs as a single instance;
     * a multi-instance service would need a DB-level lock per series instead.
     */
    @Override
    public synchronized AuthorizeInvoiceResponse authorize(AuthorizeInvoiceRequest request) {
        return transactionTemplate.execute(status -> authorizeInTransaction(request));
    }

    @Override
    public GetLastAuthorizedInvoiceResponse getLastAuthorizedInvoice(
            GetLastAuthorizedInvoiceRequest request) {

        Integer pointOfSale = request.getPointOfSale() != null
                ? request.getPointOfSale().intValue()
                : null;

        long lastInvoiceNumber = authorizedInvoiceRepository.findLastInvoiceNumber(
                request.getIssuerCuit(), pointOfSale, request.getInvoiceType());

        GetLastAuthorizedInvoiceResponse response = new GetLastAuthorizedInvoiceResponse();
        response.setIssuerCuit(request.getIssuerCuit());
        response.setPointOfSale(request.getPointOfSale());
        response.setInvoiceType(request.getInvoiceType());
        response.setLastInvoiceNumber(lastInvoiceNumber);

        return response;
    }

    // ─────────────────────────────────────────────
    //  AUTHORIZATION FLOW
    // ─────────────────────────────────────────────

    private AuthorizeInvoiceResponse authorizeInTransaction(AuthorizeInvoiceRequest request) {

        // Step 1 — a resent invoice gets its original CAE back instead of a new number
        Optional<AuthorizedInvoice> alreadyAuthorized = findAlreadyAuthorized(request);

        if (alreadyAuthorized.isPresent()) {
            log.info("Invoice already authorized — externalInvoiceId: {} | number: {}",
                    request.getExternalInvoiceId(), alreadyAuthorized.get().getInvoiceNumber());
            return buildApprovedResponse(alreadyAuthorized.get(), ALREADY_AUTHORIZED_MESSAGE);
        }

        // Step 2 — validate the incoming invoice data
        List<InvoiceValidator.ValidationError> errors = invoiceValidator.validate(request);

        // Step 3 — if there are errors, return a REJECTED response (nothing is registered)
        if (!errors.isEmpty()) {
            log.warn("Invoice rejected — externalInvoiceId: {} | errors: {}",
                    request.getExternalInvoiceId(), errors.size());
            return buildRejectedResponse(request, errors);
        }

        // Step 4 — assign the next number of the series, generate the CAE and register it
        AuthorizedInvoice invoice = authorizedInvoiceRepository.save(
                AuthorizedInvoice.builder()
                        .externalInvoiceId(request.getExternalInvoiceId())
                        .issuerCuit(request.getIssuer().getCuit())
                        .pointOfSale(request.getPointOfSale().intValue())
                        .invoiceType(request.getInvoiceType())
                        .invoiceNumber(nextInvoiceNumber(request))
                        .cae(generateCae())
                        .caeExpirationDate(LocalDate.now().plusDays(CAE_VALIDITY_DAYS))
                        .totalAmount(request.getTotalAmount())
                        .authorizedAt(LocalDateTime.now())
                        .build());

        log.info("Invoice approved — externalInvoiceId: {} | number: {} | CAE: {}",
                request.getExternalInvoiceId(), invoice.getInvoiceNumber(), invoice.getCae());

        return buildApprovedResponse(invoice, APPROVED_MESSAGE);
    }

    private Optional<AuthorizedInvoice> findAlreadyAuthorized(AuthorizeInvoiceRequest request) {
        String externalInvoiceId = request.getExternalInvoiceId();

        if (externalInvoiceId == null || externalInvoiceId.isBlank()) {
            return Optional.empty();
        }

        return authorizedInvoiceRepository.findByExternalInvoiceId(externalInvoiceId);
    }

    // ─────────────────────────────────────────────
    //  NUMBERING & CAE GENERATION
    // ─────────────────────────────────────────────

    /**
     * Next correlative number of the series: issuer CUIT + point of sale + invoice type.
     * The first invoice of a series gets number 1.
     */
    private long nextInvoiceNumber(AuthorizeInvoiceRequest request) {
        return authorizedInvoiceRepository.findLastInvoiceNumber(
                request.getIssuer().getCuit(),
                request.getPointOfSale().intValue(),
                request.getInvoiceType()) + 1;
    }

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

    // ─────────────────────────────────────────────
    //  RESPONSE BUILDERS
    // ─────────────────────────────────────────────

    private AuthorizeInvoiceResponse buildApprovedResponse(AuthorizedInvoice invoice, String message) {

        AuthorizeInvoiceResponse response = new AuthorizeInvoiceResponse();

        response.setExternalInvoiceId(invoice.getExternalInvoiceId());
        response.setStatus(AuthorizationStatus.APPROVED);
        response.setCae(invoice.getCae());
        response.setCaeExpirationDate(toXmlDate(invoice.getCaeExpirationDate()));
        response.setAfipInvoiceNumber(invoice.getInvoiceNumber());
        response.setMessage(message);
        response.setProcessedAt(toXmlDateTime(invoice.getAuthorizedAt()));

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
