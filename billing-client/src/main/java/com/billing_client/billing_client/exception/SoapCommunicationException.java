package com.billing_client.billing_client.exception;

public class SoapCommunicationException extends RuntimeException {

    public SoapCommunicationException(String message) {
        super(message);
    }

    public SoapCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}

