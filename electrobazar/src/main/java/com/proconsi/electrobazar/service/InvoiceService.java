package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.Sale;

import java.util.Optional;

public interface InvoiceService {

    /**
     * Atomically creates a new invoice for the given sale, assigning the next
     * correlative number in the current year's series.
     * This method is transactional and uses pessimistic locking on the sequence row
     * to prevent duplicate invoice numbers under concurrent requests.
     *
     * @param sale The completed sale to invoice
     * @return The newly created and persisted Invoice
     */
    Invoice createInvoice(Sale sale);

    /**
     * Returns the invoice associated with the given sale ID, if one exists.
     *
     * @param saleId The sale ID to look up
     * @return An Optional containing the Invoice, or empty if none
     */
    Optional<Invoice> findBySaleId(Long saleId);

    /**
     * Saves the PDF blob and filename into an existing Invoice.
     *
     * @param invoiceId The invoice ID
     * @param pdfData   The PDF bytes
     * @param filename  The filename
     */
    void savePdf(Long invoiceId, byte[] pdfData, String filename);
}
