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
            String filename = String.format("Cierre_Caja_%s_ID%d.pdf", dateStr, register.getId());
            File outputFile = new File(dir, filename);

            // 5. Convert HTML to PDF using OpenHTMLToPDF
            try (OutputStream os = new FileOutputStream(outputFile)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();

                // Allow fallback to generic font families if needed
                builder.useFastMode();

                // The base URI is needed for resolving relative resources (images, css) if any
                // are added later
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();
            }

            log.info("PDF report generated successfully at: {}", outputFile.getAbsolutePath());
            return outputFile;

        } catch (Exception e) {
            log.error("Error generating cash close PDF report for Register ID " + register.getId(), e);
            throw new RuntimeException("Error generating PDF report: " + e.getMessage(), e);
        }
    }
}
