package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.service.EmailService;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.PdfReportService;
import com.proconsi.electrobazar.repository.SaleRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Handles email-related tasks asynchronously so that HTTP endpoints
 * can return immediately without blocking on slow SMTP operations.
 *
 * IMPORTANT: @Async only works when the annotated method is invoked
 * through a Spring proxy (i.e. from a DIFFERENT bean). This class
 * exists specifically to allow EmailApiRestController to call
 * sendSaleEmail() on a separate bean and get true async behaviour.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncEmailService {

    private final EmailService emailService;
    private final InvoiceService invoiceService;
    private final PdfReportService pdfReportService;
    private final SaleRepository saleRepository;

    @Async
    @Transactional(readOnly = true)
    public void sendSaleEmailAsync(Sale sale, String email) {
        try {
            // Re-fetch sale eagerly since it may be detached or a non-initialized proxy
            Sale eagerSale = saleRepository.findById(sale.getId())
                .orElseThrow(() -> new RuntimeException("Sale not found: " + sale.getId()));

            byte[] pdfContent;
            String filename;

            var invoiceOpt = invoiceService.findBySaleId(eagerSale.getId());
            if (invoiceOpt.isPresent()) {
                Invoice invoice = invoiceOpt.get();
                pdfContent = pdfReportService.generateInvoicePdf(invoice);
                filename = "Factura_" + invoice.getInvoiceNumber() + ".pdf";
            } else {
                pdfContent = pdfReportService.generateReceiptPdf(eagerSale);
                filename = "Ticket_" + eagerSale.getId() + ".pdf";
            }

            String subject = "Su documento de compra - Electrobazar";
            String body = "Estimado cliente,\n\nAdjunto encontrará el documento de su compra.\n\nGracias por confiar en Electrobazar.";

            emailService.sendEmailWithAttachment(email, subject, body, filename, pdfContent);
            log.info("Email sent successfully for Sale ID {} to {}", sale.getId(), email);
        } catch (Exception e) {
            log.error("Error sending email for Sale ID {} to {}: {}", sale.getId(), email, e.getMessage(), e);
        }
    }
}
