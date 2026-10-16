package com.afip.soap.server.repository;

import com.afip.soap.server.model.AuthorizedInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import server.soap.afip.invoice.InvoiceType;

import java.util.Optional;

public interface AuthorizedInvoiceRepository extends JpaRepository<AuthorizedInvoice, Long> {

    Optional<AuthorizedInvoice> findByExternalInvoiceId(String externalInvoiceId);

    /**
     * Last number authorized for a series, or 0 if the series has no invoices yet.
     */
    @Query("""
            select coalesce(max(a.invoiceNumber), 0)
            from AuthorizedInvoice a
            where a.issuerCuit = :issuerCuit
              and a.pointOfSale = :pointOfSale
              and a.invoiceType = :invoiceType
            """)
    long findLastInvoiceNumber(@Param("issuerCuit") String issuerCuit,
                               @Param("pointOfSale") Integer pointOfSale,
                               @Param("invoiceType") InvoiceType invoiceType);
}
