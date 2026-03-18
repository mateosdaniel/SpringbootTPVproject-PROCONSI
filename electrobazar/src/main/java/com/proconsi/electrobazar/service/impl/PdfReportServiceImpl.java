package com.proconsi.electrobazar.service.impl;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;
import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.repository.SaleReturnRepository;
import com.proconsi.electrobazar.service.CompanySettingsService;
import com.proconsi.electrobazar.service.PdfReportService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final CompanySettingsService companySettingsService;
    private final RecargoEquivalenciaCalculator recargoCalculator;

    @Override
    public byte[] generateCashCloseReport(CashRegister register) {
        log.info("Generating cash close PDF report for Register ID {}", register.getId());
        try {
            Context context = new Context();
            context.setVariable("register", register);
            context.setVariable("pdfMode", true);

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
            context.setVariable("pdfMode", true);

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

    @Override
    public byte[] generateInvoicePdf(Invoice invoice) {
        log.info("Generating PDF for Invoice {}", invoice.getInvoiceNumber());
        try {
            Sale sale = invoice.getSale();
            Context context = populateSaleContext(sale);
            context.setVariable("invoice", invoice);

            String htmlContent = templateEngine.process("tpv/invoice", context);
            htmlContent = cleanHtmlForPdf(htmlContent);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();
                return os.toByteArray();
            }
        } catch (Exception e) {
            log.error("PDF generation failed for Invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            throw new RuntimeException("Could not generate invoice PDF.", e);
        }
    }

    @Override
    public byte[] generateReceiptPdf(Sale sale) {
        log.info("Generating PDF for Sale receipt ID {}", sale.getId());
        try {
            Context context = populateSaleContext(sale);

            String htmlContent = templateEngine.process("tpv/receipt", context);
            htmlContent = cleanHtmlForPdf(htmlContent);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
                builder.toStream(os);
                builder.run();
                return os.toByteArray();
            }
        } catch (Exception e) {
            log.error("PDF generation failed for Sale ID {}: {}", sale.getId(), e.getMessage());
            throw new RuntimeException("Could not generate receipt PDF.", e);
        }
    }

    private Context populateSaleContext(Sale sale) {
        Context context = new Context();
        context.setVariable("sale", sale);
        context.setVariable("companySettings", companySettingsService.getSettings());
        context.setVariable("pdfMode", true);

        boolean applyRecargo = sale.getCustomer() != null
                && Boolean.TRUE.equals(sale.getCustomer().getHasRecargoEquivalencia());
        List<TaxBreakdown> breakdowns = new ArrayList<>();
        for (SaleLine line : sale.getLines()) {
            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            TaxBreakdown bd = recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(), line.getProduct().getName(),
                    line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo);
            breakdowns.add(bd);
        }
        context.setVariable("taxBreakdowns", breakdowns);
        context.setVariable("applyRecargo", applyRecargo);

        BigDecimal totalBase = breakdowns.stream().map(TaxBreakdown::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalVat = breakdowns.stream().map(TaxBreakdown::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRecargo = breakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        context.setVariable("totalBase", totalBase);
        context.setVariable("totalVat", totalVat);
        context.setVariable("totalRecargo", totalRecargo);
        
        return context;
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
                   .replace("&euro;", "&#8364;")
                   .replace("&middot;", "&#183;")
                   .replace("&mdash;", "&#8212;")
                   .replace("&ndash;", "&#8211;")
                   .replace("&iexcl;", "&#161;")
                   .replace("&iquest;", "&#191;")
                   .replace("&ordm;", "&#186;")
                   .replace("&orda;", "&#170;")
                   .replace("&aacute;", "&#225;")
                   .replace("&eacute;", "&#233;")
                   .replace("&iacute;", "&#237;")
                   .replace("&oacute;", "&#243;")
                   .replace("&uacute;", "&#250;")
                   .replace("&ntilde;", "&#241;")
                   // Robustly escape any leftover unescaped ampersands to satisfy SAX XML parser
                   .replaceAll("&(?!(?:[a-zA-Z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);)", "&amp;");
    }
}


