package com.billing_client.billing_client.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled jobs (see PendingInvoiceRetryJob).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
