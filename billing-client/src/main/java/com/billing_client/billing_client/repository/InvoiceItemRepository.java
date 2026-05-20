package com.billing_client.billing_client.repository;

import com.billing_client.billing_client.model.InvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    /**
     * Finds all items belonging to a specific invoice.
     */
    List<InvoiceItem> findByInvoiceId(Long invoiceId);
}