package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Invoice} entities.
 * Manages official fiscal invoices linked to completed sales.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Finds the fiscal invoice associated with a specific sale ID.
     * @param saleId The ID of the sale.
     * @return Optional containing the invoice if generated.
     */
    @Query("SELECT i FROM Invoice i WHERE i.sale.id = :saleId")
    Optional<Invoice> findBySaleId(@Param("saleId") Long saleId);

    /**
     * Finds an invoice by its unique formatted number (e.g., "F-2026-0001").
     * @param invoiceNumber The formatted number.
     * @return Optional containing the matching invoice.
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}


