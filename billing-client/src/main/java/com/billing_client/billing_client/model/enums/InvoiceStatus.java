package com.billing_client.billing_client.model.enums;

public enum InvoiceStatus {
    PENDING,    // just created, waiting for AFIP response
    APPROVED,   // AFIP returned a valid CAE
    REJECTED    // AFIP rejected the invoice
}
