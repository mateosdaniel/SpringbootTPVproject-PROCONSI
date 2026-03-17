package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.Sale;
import java.util.Optional;

/**
 * Interface defining operations for managing legal invoices.
 * Handles sequence generation and associations with sales.
 */
public interface InvoiceService {

    /**
     * Creates a new legal invoice for the specified sale.
     * Uses pessimistic locking to ensure unique sequence numbers.
     *
     * @param sale The completed sale to be invoiced.
     * @return The newly created and persisted Invoice.
     */
    Invoice createInvoice(Sale sale);

    /**
     * Looks up the invoice associated with a specific sale.
     *
     * @param saleId The ID of the sale.
     * @return An Optional containing the Invoice, if found.
     */
    Optional<Invoice> findBySaleId(Long saleId);
}
