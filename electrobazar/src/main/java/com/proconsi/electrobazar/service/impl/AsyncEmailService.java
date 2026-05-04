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

    private static final String HTML_TEMPLATE = 
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">" +
        "<style>" +
        "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4; }" +
        ".container { width: 100%; max-width: 600px; margin: 0 auto; background-color: #ffffff; }" +
        ".preheader { display: none; font-size: 1px; color: #f4f4f4; line-height: 1px; max-height: 0px; max-width: 0px; opacity: 0; overflow: hidden; }" +
        ".header { background-color: #ffcc00; padding: 30px; text-align: center; }" +
        ".header h1 { margin: 0; color: #000; font-size: 28px; letter-spacing: 2px; font-weight: 900; }" +
        ".hero { width: 100%; height: auto; display: block; border: 0; }" +
        ".content { padding: 40px; color: #333; line-height: 1.6; }" +
        ".content h2 { color: #000; margin-top: 0; font-size: 22px; }" +
        ".details-box { background-color: #f8f8f8; border: 1px solid #eeeeee; padding: 20px; border-radius: 8px; margin: 25px 0; }" +
        ".details-box p { margin: 5px 0; font-size: 14px; color: #555; }" +
        ".details-box strong { color: #000; }" +
        ".footer { background-color: #e3000f; color: #ffffff; padding: 30px; text-align: center; font-size: 13px; }" +
        ".footer a { color: #ffffff; text-decoration: underline; margin: 0 10px; }" +
        ".contact-info { margin-top: 20px; font-size: 14px; text-align: center; color: #666; padding: 0 40px 20px; }" +
        "</style></head><body>" +
        "<div class=\"preheader\">Tu justificante de compra de Electrobazar ya está disponible.</div>" +
        "<div class=\"container\">" +
        "  <div class=\"header\"><h1>ELECTROBAZAR</h1></div>" +
        "  <img src=\"https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?ixlib=rb-1.2.1&auto=format&fit=crop&w=1350&q=80\" alt=\"Compra exitosa\" class=\"hero\">" +
        "  <div class=\"content\">" +
        "    <h2>¡Gracias por tu compra, {{nombre_cliente}}!</h2>" +
        "    <p>Es un placer saludarte. Queremos confirmarte que hemos procesado tu pedido correctamente. Adjunto a este mensaje encontrarás el documento legal de tu compra en formato PDF.</p>" +
        "    <div class=\"details-box\">" +
        "      <p><strong>Referencia:</strong> {{referencia_factura}}</p>" +
        "      <p><strong>Fecha:</strong> {{fecha_compra}}</p>" +
        "    </div>" +
        "    <p>Esperamos que disfrutes de tu adquisición. Si tienes cualquier duda o necesitas soporte técnico, nuestro equipo estará encantado de ayudarte respondiendo directamente a este correo.</p>" +
        "  </div>" +
        "  <div class=\"contact-info\">" +
        "    ¿Tienes dudas? Escríbenos a <a href=\"mailto:soporte@electrobazar.com\" style=\"color: #e3000f; font-weight: bold;\">soporte@electrobazar.com</a>" +
        "  </div>" +
        "  <div class=\"footer\">" +
        "    <p>&copy; {{year}} Electrobazar SL. Todos los derechos reservados.</p>" +
        "    <p><a href=\"#\">Web Oficial</a> | <a href=\"#\">Mi Cuenta</a> | <a href=\"#\">Contacto</a></p>" +
        "  </div>" +
        "</div></body></html>";

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

            // Prepare dynamic data
            String customerName = (eagerSale.getCustomer() != null) ? eagerSale.getCustomer().getName() : "cliente";
            String dateStr = eagerSale.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String docType = invoiceOpt.isPresent() ? "factura" : "ticket";
            String docRef = invoiceOpt.isPresent() ? invoiceOpt.get().getInvoiceNumber() : "#" + eagerSale.getId();
            String currentYear = String.valueOf(java.time.Year.now().getValue());

            // Build Subject
            String subject = "Su " + docType + " de compra " + docRef + " - Electrobazar";
            
            // Build HTML Body by replacing placeholders
            String htmlBody = HTML_TEMPLATE
                .replace("{{nombre_cliente}}", customerName)
                .replace("{{referencia_factura}}", docRef)
                .replace("{{fecha_compra}}", dateStr)
                .replace("{{year}}", currentYear);

            // Send using EmailService (which uses MimeMessageHelper internally)
            emailService.sendEmailWithAttachment(email, subject, htmlBody, filename, pdfContent);
            log.info("HTML Email sent successfully for Sale ID {} to {}", sale.getId(), email);
        } catch (Exception e) {
            log.error("Error sending HTML email for Sale ID {} to {}: {}", sale.getId(), email, e.getMessage(), e);
        }
    }
}
