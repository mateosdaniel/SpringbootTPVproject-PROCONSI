package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.service.EmailService;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.PdfReportService;
import com.proconsi.electrobazar.service.SaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailApiRestController {

    private final EmailService emailService;
    private final SaleService saleService;
    private final InvoiceService invoiceService;
    private final PdfReportService pdfReportService;

    @PostMapping("/send-sale/{saleId}")
    public ResponseEntity<?> sendSaleEmail(@PathVariable Long saleId, @RequestParam String email) {
        log.info("Request to send sale email for Sale ID {} to {}", saleId, email);
        try {
            Sale sale = saleService.findById(saleId);
            if (sale == null) {
                return ResponseEntity.status(404).body(Collections.singletonMap("error", "Sale not found"));
            }

            // Check if it has an invoice
            byte[] pdfContent;
            String filename;
            var invoiceOpt = invoiceService.findBySaleId(saleId);
            if (invoiceOpt.isPresent()) {
                Invoice invoice = invoiceOpt.get();
                pdfContent = pdfReportService.generateInvoicePdf(invoice);
                filename = "Factura_" + invoice.getInvoiceNumber() + ".pdf";
            } else {
                pdfContent = pdfReportService.generateReceiptPdf(sale);
                filename = "Ticket_" + sale.getId() + ".pdf";
            }

            String subject = "Su documento de compra - Electrobazar";
            String body = "Estimado cliente,\n\nAdjunto encontrará el documento de su compra.\n\nGracias por confiar en Electrobazar.";

            emailService.sendEmailWithAttachment(email, subject, body, filename, pdfContent);

            return ResponseEntity.ok(Collections.singletonMap("message", "Email sent successfully"));
        } catch (Exception e) {
            log.error("Error sending email", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Failed to send email: " + e.getMessage()));
        }
    }
}
