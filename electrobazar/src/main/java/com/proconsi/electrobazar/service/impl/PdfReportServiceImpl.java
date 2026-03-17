package com.proconsi.electrobazar.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.SaleReturn;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;
import com.proconsi.electrobazar.repository.SaleReturnRepository;
import com.proconsi.electrobazar.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of {@link PdfReportService}.
 * Uses Thymeleaf for template processing and OpenHTMLtoPDF for PDF rendering.
 * Converts HTML5/CSS3 templates into professional PDF documents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportServiceImpl implements PdfReportService {

    private final TemplateEngine templateEngine;
    private final SaleReturnRepository saleReturnRepository;
    private final com.proconsi.electrobazar.service.CompanySettingsService companySettingsService;

    @Override
    public byte[] generateCashCloseReport(CashRegister register) {
        log.info("Generating cash close PDF report for Register ID {}", register.getId());
        try {
            Context context = new Context();
            context.setVariable("register", register);

            LocalDateTime start = register.getOpeningTime() != null ? register.getOpeningTime() : register.getRegisterDate().atStartOfDay();
            LocalDateTime end = register.getClosedAt() != null ? register.getClosedAt() : LocalDateTime.now();
            
            List<SaleReturn> returns = saleReturnRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
            context.setVariable("returns", returns);

            String htmlContent = templateEngine.process("reports/cash-close-report", context);
            htmlContent = cleanHtmlForPdf(htmlContent);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();

                return os.toByteArray();
            }
        } catch (Exception e) {
            log.error("PDF generation failed for Register ID {}: {}", register.getId(), e.getMessage());
            throw new RuntimeException("Could not generate cash close PDF.", e);
        }
    }

    @Override
    public byte[] generateTariffSheet(Tariff tariff, List<TariffPriceEntryDTO> history, java.time.LocalDate date) {
        log.info("Generating tariff PDF sheet for Tariff ID {} at date {}", tariff.getId(), date);
        try {
            Context context = new Context();
            context.setVariable("tariff", tariff);
            context.setVariable("history", history);
            context.setVariable("generationDate", LocalDateTime.now());
            context.setVariable("targetDate", date);
            context.setVariable("companySettings", companySettingsService.getSettings());

            Map<String, List<TariffPriceEntryDTO>> grouped = history.stream()
                    .collect(Collectors.groupingBy(
                            h -> h.getCategoryName() != null ? h.getCategoryName() : "Uncategorized"
                    ));
            context.setVariable("groupedHistory", grouped);

            String htmlContent = templateEngine.process("reports/tariff-sheet", context);
            htmlContent = cleanHtmlForPdf(htmlContent);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();

                return os.toByteArray();
            }
        } catch (Exception e) {
            log.error("PDF generation failed for Tariff ID {}: {}", tariff.getId(), e.getMessage());
            throw new RuntimeException("Could not generate tariff sheet PDF.", e);
        }
    }

    /**
     * Pre-processes HTML to make it compatible with OpenHTMLtoPDF's strict XML parser.
     * Ensures void tags are closed and entities are properly escaped.
     */
    private String cleanHtmlForPdf(String html) {
        if (html == null) return "";
        return html.replaceAll("<(meta|br|hr|img|input|link)([^>]*?)(?<!/)>", "<$1$2 />")
                   .replace("&copy;", "&#169;")
                   .replace("&reg;", "&#174;")
                   .replace("&trade;", "&#8482;")
                   .replace("&nbsp;", "&#160;")
                   .replace("&euro;", "&#8364;");
    }
}


