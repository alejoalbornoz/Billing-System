package com.billing_client.billing_client.scheduler;

import com.billing_client.billing_client.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Resends to AFIP the invoices left PENDING because AFIP could not be reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingInvoiceRetryJob {

    private final InvoiceService invoiceService;

    /**
     * Only invoices older than this are resent, so an invoice whose first
     * AFIP call is still in progress is never sent twice.
     * Must be longer than the SOAP connect + read timeouts.
     */
    @Value("${afip.retry.min-age-ms}")
    private long minAgeMs;

    @Scheduled(
            fixedDelayString = "${afip.retry.interval-ms}",
            initialDelayString = "${afip.retry.interval-ms}"
    )
    public void resendPendingInvoices() {
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(minAgeMs));

        int resent = invoiceService.resendPendingInvoices(cutoff);

        if (resent > 0) {
            log.info("Retry job — resent {} pending invoice(s) to AFIP", resent);
        }
    }
}
