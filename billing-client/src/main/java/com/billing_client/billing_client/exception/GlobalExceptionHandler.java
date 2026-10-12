package com.billing_client.billing_client.exception;


import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Extends ResponseEntityExceptionHandler so Spring MVC's own exceptions
 * (malformed JSON, wrong path variable type, unsupported method, unknown route...)
 * keep their correct 4xx status instead of falling into the generic 500 handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ─────────────────────────────────────────────
    //  REQUEST ERRORS (Spring MVC)
    // ─────────────────────────────────────────────

    /**
     * Handles @Valid validation errors on request DTOs.
     *
     * Example: missing required field, wrong CUIT format, negative amount.
     * Returns 400 BAD REQUEST with a map of field -> error message.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        // Class-level constraints are not tied to a single field
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.put(error.getObjectName(), error.getDefaultMessage()));

        log.warn("Validation failed — fields: {}", fieldErrors);

        ErrorResponse response = buildError(HttpStatus.BAD_REQUEST,
                "Validation Failed", "One or more fields are invalid.");
        response.setFieldErrors(fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handles request bodies that cannot be parsed: malformed JSON,
     * an unknown enum value ("invoiceType": "FOO"), text in a numeric field...
     * Returns 400 BAD REQUEST pointing at the offending field when possible.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String message = "Request body is missing or is not valid JSON.";

        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {

            message = "Invalid value for field '" + toFieldPath(mismatch) + "'.";

            if (mismatch instanceof InvalidFormatException invalidFormat
                    && invalidFormat.getTargetType() != null
                    && invalidFormat.getTargetType().isEnum()) {
                message += " Accepted values: "
                        + Arrays.toString(invalidFormat.getTargetType().getEnumConstants());
            }
        }

        log.warn("Unreadable request body — {}", message);

        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, "Malformed Request", message));
    }

    /**
     * Every other exception handled by ResponseEntityExceptionHandler ends up here.
     * Keeps the status Spring chose and only swaps the body for our ErrorResponse.
     *
     * Example: GET /api/v1/invoices/abc → 400, DELETE /api/v1/invoices → 405.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status != null ? status.getReasonPhrase() : "Error";
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : ex.getMessage();

        log.warn("Request failed — status: {} | {}", statusCode.value(), message);

        return super.handleExceptionInternal(
                ex, buildError(statusCode, error, message), headers, statusCode, request);
    }

    // ─────────────────────────────────────────────
    //  DOMAIN ERRORS
    // ─────────────────────────────────────────────

    /**
     * Handles invoice not found errors.
     * Returns 404 NOT FOUND.
     */
    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInvoiceNotFound(
            InvoiceNotFoundException ex) {

        log.warn("Invoice not found — {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(HttpStatus.NOT_FOUND, "Invoice Not Found", ex.getMessage()));
    }

    /**
     * Handles SOAP communication errors with AFIP server.
     * Returns 502 BAD GATEWAY — the request was valid but upstream failed.
     */
    @ExceptionHandler(SoapCommunicationException.class)
    public ResponseEntity<ErrorResponse> handleSoapCommunicationError(
            SoapCommunicationException ex) {

        log.error("SOAP communication error — {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(buildError(HttpStatus.BAD_GATEWAY, "AFIP Communication Error",
                        "Failed to communicate with AFIP server. Please try again later."));
    }

    /**
     * Handles registration with an email that is already taken.
     * Returns 409 CONFLICT.
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex) {

        log.warn("Email already exists — {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, "Email Already Exists", ex.getMessage()));
    }

    // ─────────────────────────────────────────────
    //  SECURITY ERRORS
    // ─────────────────────────────────────────────

    /**
     * Handles failed logins (wrong password, unknown email, disabled account).
     * The message is intentionally generic so it doesn't reveal which emails exist.
     * Returns 401 UNAUTHORIZED.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(
            AuthenticationException ex) {

        log.warn("Authentication failed — {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, "Authentication Failed",
                        "Invalid email or password."));
    }

    /**
     * Denials from @PreAuthorize are rethrown so Spring Security handles them:
     * anonymous users get 401 and users without the required role get 403
     * (see SecurityConfig). Otherwise the catch-all below would turn them into 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    // ─────────────────────────────────────────────
    //  FALLBACK
    // ─────────────────────────────────────────────

    /**
     * Catches any unexpected exception.
     * Returns 500 INTERNAL SERVER ERROR.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {

        log.error("Unexpected error — {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        "An unexpected error occurred. Please contact support."));
    }

    // ─────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────

    private ErrorResponse buildError(HttpStatusCode status, String error, String message) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /** Builds a readable path such as "items[0].vatRate" from Jackson's reference chain. */
    private String toFieldPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();

        for (JsonMappingException.Reference reference : ex.getPath()) {
            if (reference.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getFieldName());
            } else {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }

        return path.toString();
    }
}
