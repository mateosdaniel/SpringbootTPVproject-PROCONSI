package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Find the invoice associated with a given sale. */
    @Query("SELECT i FROM Invoice i WHERE i.sale.id = :saleId")
    Optional<Invoice> findBySaleId(@Param("saleId") Long saleId);

    /** Find by formatted invoice number (e.g. "F-2026-0001"). */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
}
