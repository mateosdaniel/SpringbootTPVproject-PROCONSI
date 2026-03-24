package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.RectificativeInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link RectificativeInvoice} entities.
 * Manages official corrective documents issued to rectify previously generated invoices.
 */
@Repository
public interface RectificativeInvoiceRepository extends JpaRepository<RectificativeInvoice, Long> {

    /**
     * Finds the corrective invoice associated with a specific merchandise return.
     * @param returnId ID of the SaleReturn.
     * @return Optional containing the corrective invoice.
     */
    Optional<RectificativeInvoice> findBySaleReturnId(Long returnId);

    /**
     * Finds the very last corrective invoice issued, used for Verifactu chaining.
     */
    Optional<RectificativeInvoice> findFirstByOrderByCreatedAtDesc();
}


