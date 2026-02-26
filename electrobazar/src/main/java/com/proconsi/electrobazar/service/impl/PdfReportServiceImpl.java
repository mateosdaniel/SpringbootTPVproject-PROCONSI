package com.proconsi.electrobazar.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportServiceImpl implements PdfReportService {

    private final TemplateEngine templateEngine;

    // Directory where PDFs will be saved
    private static final String PDF_DIRECTORY = "cierres_de_caja";

    @Override
    public File generateCashCloseReport(CashRegister register) {
        try {
            // 1. Prepare Thymeleaf context with variables
            Context context = new Context();
            context.setVariable("register", register);

            // 2. Process HTML template
            String htmlContent = templateEngine.process("reports/cash-close-report", context);

            // 3. Ensure target directory exists
            File dir = new File(PDF_DIRECTORY);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 4. Generate filename based on date and ID
            String dateStr = register.getClosedAt() != null
                    ? register.getClosedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    : "UnknownDate";
            String filename = String.format("Cierre_Caja_%d_%s.pdf", register.getId(), dateStr);
            File outputFile = new File(dir, filename);

            // 5. Convert HTML to PDF using OpenHTMLToPDF
            try (OutputStream os = new FileOutputStream(outputFile)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();

                // builder.useFastMode(); // Removed for better compatibility

                // The base URI is needed for resolving relative resources (images, css) if any
                // are added later
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();
                os.flush();
            }

            log.info("PDF report generated successfully at: {} (Size: {} bytes)",
                    outputFile.getAbsolutePath(), outputFile.length());
            return outputFile;

        } catch (Exception e) {
            log.error("Error generating cash close PDF report for Register ID " + register.getId(), e);
            throw new RuntimeException("Error generating PDF report: " + e.getMessage(), e);
        }
    }

    private static final String INVOICE_PDF_DIRECTORY = "facturas";

    @Override
    public File generateInvoiceReport(com.proconsi.electrobazar.model.Sale sale) {
        try {
            // 1. Prepare Thymeleaf context with variables
            Context context = new Context();
            context.setVariable("sale", sale);

            // 2. Process HTML template
            String htmlContent = templateEngine.process("reports/invoice-report", context);

            // 3. Ensure target directory exists
            File dir = new File(INVOICE_PDF_DIRECTORY);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 4. Generate filename based on date and ID
            String dateStr = sale.getCreatedAt() != null
                    ? sale.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    : "UnknownDate";
            String filename = String.format("Factura_Ticket_%d_%s.pdf", sale.getId(), dateStr);
            File outputFile = new File(dir, filename);

            // 5. Convert HTML to PDF using OpenHTMLToPDF
            try (OutputStream os = new FileOutputStream(outputFile)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();

                // builder.useFastMode(); // Removed for better compatibility

                // The base URI is needed for resolving relative resources (images, css) if any
                // are added later
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();
                os.flush();
            }

            log.info("Invoice PDF report generated successfully at: {} (Size: {} bytes)",
                    outputFile.getAbsolutePath(), outputFile.length());
            return outputFile;

        } catch (Exception e) {
            log.error("Error generating invoice PDF report for Sale ID " + sale.getId(), e);
            throw new RuntimeException("Error generating PDF report: " + e.getMessage(), e);
        }
    }
}
