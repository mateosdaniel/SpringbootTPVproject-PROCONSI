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

    /**
     * Generates a corrective invoice (F series) with negative amounts.
     * Links the new record to the original invoice as its rectification.
     * 
     * @param originalSale The sale being cancelled/rectified.
     * @param reason       The mandatory reason for rectification.
     * @return The negative rectificative Invoice.
     */
    Invoice generateRectificativeInvoice(Sale originalSale, String reason);

    /**
     * Verifies the integrity of the Verifactu hash chain for a given serie.
     * Recalculates all hashes and compares them with stored values.
     *
     * @param serie The invoice serie to audit (e.g. "F").
     * @return true if the chain is intact, false if any record has been tampered.
     */
    boolean verifyChain(String serie);

    /** Calcula la Huella Verifactu de una factura completa (F1). */
    String calculateHash(Invoice invoice, String previousHash);

    /**
     * Generates the official Verifactu QR code content and image as Base64.
     * The URL points to the AEAT verification portal.
     *
     * @param invoice The invoice to represent.
     * @return Base64 encoded PNG image for displaying in templates.
     */
    String generateQrCodeBase64(Invoice invoice);

    /**
     * Overload for corrective invoices.
     */
    String generateQrCodeBase64(com.proconsi.electrobazar.model.RectificativeInvoice rect);

    /**
     * Calculates the Verifactu hash for a corrective invoice.
     */
    String calculateHash(com.proconsi.electrobazar.model.RectificativeInvoice rect, String previousHash);

    /**
     * Overload for simplified tickets.
     */
    String generateQrCodeBase64(com.proconsi.electrobazar.model.Ticket ticket);

    /**
     * Calculates the Verifactu hash for a simplified ticket.
     */
    String calculateHash(com.proconsi.electrobazar.model.Ticket ticket, String previousHash);
}
