package com.proconsi.electrobazar.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportServiceImpl implements PdfReportService {

    private final TemplateEngine templateEngine;
    private final com.proconsi.electrobazar.repository.SaleReturnRepository saleReturnRepository;

    @Override
    public byte[] generateCashCloseReport(CashRegister register) {
        log.info("Generating cash close PDF report for Register ID {}", register.getId());
        try {
            // 1. Prepare Thymeleaf context with variables
            Context context = new Context();
            context.setVariable("register", register);

            // Fetch returns for this session
            java.time.LocalDateTime start = register.getOpeningTime() != null ? register.getOpeningTime()
                    : register.getRegisterDate().atStartOfDay();
            java.time.LocalDateTime end = register.getClosedAt() != null ? register.getClosedAt()
                    : java.time.LocalDateTime.now();
            List<com.proconsi.electrobazar.model.SaleReturn> returns = saleReturnRepository
                    .findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
            context.setVariable("returns", returns);

            // 2. Process HTML template
            String htmlContent = templateEngine.process("reports/cash-close-report", context);

            // 3. Convert HTML to PDF using OpenHTMLToPDF in-memory
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();

                byte[] pdfBytes = os.toByteArray();
                log.info("Cash close PDF generated successfully (Size: {} bytes)", pdfBytes.length);
                return pdfBytes;
            }

        } catch (Exception e) {
            log.error("Error generating cash close PDF report for Register ID " + register.getId(), e);
            throw new RuntimeException("Error generating PDF report: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] generateInvoiceReport(
            Sale sale,
            Invoice invoice,
            List<TaxBreakdown> taxBreakdowns,
            Boolean applyRecargo,
            BigDecimal totalBase,
            BigDecimal totalVat,
            BigDecimal totalRecargo) {
        log.info("Generating invoice PDF report for Sale ID {}", sale.getId());
        try {
            // 1. Prepare Thymeleaf context with variables
            Context context = new Context();
            context.setVariable("sale", sale);
            context.setVariable("invoice", invoice);
            context.setVariable("taxBreakdowns", taxBreakdowns);
            context.setVariable("applyRecargo", applyRecargo);
            context.setVariable("totalBase", totalBase);
            context.setVariable("totalVat", totalVat);
            context.setVariable("totalRecargo", totalRecargo);

            // 2. Process HTML template
            String htmlContent = templateEngine.process("reports/invoice-report", context);

            // 3. Convert HTML to PDF using OpenHTMLToPDF in-memory
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "");
                builder.toStream(os);
                builder.run();

                byte[] pdfBytes = os.toByteArray();
                log.info("Invoice PDF generated successfully (Size: {} bytes)", pdfBytes.length);
                return pdfBytes;
            }

        } catch (Exception e) {
            log.error("Error generating invoice PDF report for Sale ID " + sale.getId(), e);
            throw new RuntimeException("Error generating PDF report: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] generateTicketReport(
            Sale sale,
            List<TaxBreakdown> taxBreakdowns,
            Boolean applyRecargo,
            BigDecimal totalBase,
            BigDecimal totalVat,
            BigDecimal totalRecargo) {

        log.info("Generating ticket PDF report for Sale ID {}", sale.getId());
        String htmlContent = ""; // Lo declaro fuera para poder imprimirlo si falla

        try {
            // 1. Prepare Thymeleaf context with variables
            Context context = new Context();
            context.setVariable("sale", sale);
            context.setVariable("taxBreakdowns", taxBreakdowns);
            context.setVariable("applyRecargo", applyRecargo);
            context.setVariable("totalBase", totalBase);
            context.setVariable("totalVat", totalVat);
            context.setVariable("totalRecargo", totalRecargo);

            // 2. Process HTML template
            htmlContent = templateEngine.process("reports/ticket-report", context);

            // 3. Convert HTML to PDF using OpenHTMLToPDF in-memory
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();

                // FIX: Usar string vacío "" en lugar de "classpath:/templates/"
                // Esto evita que Railway bloquee la generación por problemas de resolución de
                // rutas
                builder.withHtmlContent(htmlContent, "");
                builder.toStream(os);
                builder.run();

                byte[] pdfBytes = os.toByteArray();
                log.info("Ticket PDF generated successfully (Size: {} bytes)", pdfBytes.length);
                return pdfBytes;
            }

        } catch (Exception e) {
            // SALVAVIDAS: Si falla, esto nos escupirá el HTML exacto que está atragantando
            // al PDF
            log.error("================ ERROR HTML CONTENT ================");
            log.error(htmlContent);
            log.error("====================================================");
            log.error("Error generating ticket PDF report for Sale ID " + sale.getId(), e);
            throw new RuntimeException("Error generating PDF report: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] generateSaleDocument(
            Sale sale,
            com.proconsi.electrobazar.model.Invoice invoice,
            java.util.List<com.proconsi.electrobazar.dto.TaxBreakdown> taxBreakdowns,
            Boolean applyRecargo,
            java.math.BigDecimal totalBase,
            java.math.BigDecimal totalVat,
            java.math.BigDecimal totalRecargo) {

        if (invoice != null) {
            return generateInvoiceReport(sale, invoice, taxBreakdowns, applyRecargo, totalBase, totalVat, totalRecargo);
        } else {
            return generateTicketReport(sale, taxBreakdowns, applyRecargo, totalBase, totalVat, totalRecargo);
        }
    }
}
