package com.billing_client.billing_client.model.enums;

public enum InvoiceType {
    INVOICE_A,    // Factura A - registered taxpayers
    INVOICE_B,    // Factura B - end consumers
    INVOICE_C,    // Factura C - non-registered taxpayers
    CREDIT_NOTE,  // Nota de crédito
    DEBIT_NOTE    // Nota de débito
}
