package com.billing_client.billing_client.scheduler;

import com.billing_client.billing_client.service.InvoiceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingInvoiceRetryJob Tests")
class PendingInvoiceRetryJobTest {

    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private PendingInvoiceRetryJob retryJob;

    @Test
    @DisplayName("Should only resend invoices older than the configured min age")
    void shouldResendInvoicesOlderThanMinAge() {
        ReflectionTestUtils.setField(retryJob, "minAgeMs", 120_000L);

        retryJob.resendPendingInvoices();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(invoiceService).resendPendingInvoices(cutoff.capture());

        assertThat(cutoff.getValue())
                .isCloseTo(LocalDateTime.now().minusMinutes(2), within(5, SECONDS));
    }
}
