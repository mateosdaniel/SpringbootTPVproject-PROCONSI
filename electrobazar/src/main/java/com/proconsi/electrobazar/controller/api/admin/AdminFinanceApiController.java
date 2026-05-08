package com.proconsi.electrobazar.controller.api.admin;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.dto.*;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for managing finance-related admin tasks (Cash closings, Returns, Tariffs).
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminFinanceApiController {

    private final CashRegisterService cashRegisterService;
    private final ReturnService returnService;
    private final TariffService tariffService;
    private final TariffPriceHistoryService tariffPriceHistoryService;
    private final PdfReportService pdfReportService;
    private final CompanySettingsService companySettingsService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final TemplateEngine templateEngine;

    @GetMapping("/cash-closings")
    public ResponseEntity<Map<String, Object>> getCashClosingsPage(
            @RequestParam(required = false) String worker,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "openingTime", "closedAt", "difference", "totalSales");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        org.springframework.data.domain.Slice<AdminCashRegisterProjection> sliceData = cashRegisterService.findAdminListing(worker, date, pageable);

        List<AdminCashClosingListingDTO> list = sliceData.getContent().stream()
                .map(r -> AdminCashClosingListingDTO.builder()
                        .id(r.getId())
                        .openingTime(r.getOpeningTime())
                        .closedAt(r.getClosedAt())
                        .openingBalance(r.getOpeningBalance())
                        .totalSales(r.getTotalSales())
                        .totalCalculated((r.getOpeningBalance() != null ? r.getOpeningBalance() : BigDecimal.ZERO)
                                .add(r.getTotalSales() != null ? r.getTotalSales() : BigDecimal.ZERO))
                        .closingBalance(r.getClosingBalance())
                        .difference(r.getDifference())
                        .workerUsername(r.getWorkerUsername() != null ? r.getWorkerUsername() : "Sistema")
                        .build())
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", sliceData.getNumber());
        response.put("hasNext", sliceData.hasNext());
        response.put("first", sliceData.isFirst());
        response.put("last", !sliceData.hasNext());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/returns")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getReturnsPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "returnNumber", "createdAt", "totalRefunded", "type");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        org.springframework.data.domain.Slice<AdminReturnProjection> sliceData = returnService.findAdminListing(search, method, date, pageable);

        List<AdminReturnListingDTO> list = sliceData.getContent().stream().map(r -> AdminReturnListingDTO.builder()
                .id(r.getId())
                .returnNumber(r.getReturnNumber())
                .originalNumber(r.getOriginalInvoiceNumber() != null
                        ? r.getOriginalInvoiceNumber()
                        : (r.getOriginalTicketNumber() != null ? r.getOriginalTicketNumber()
                                : "#" + r.getOriginalSaleId()))
                .createdAt(r.getCreatedAt())
                .type(r.getType() != null ? r.getType().name() : "Desconocido")
                .reason(r.getReason())
                .workerUsername(r.getWorkerUsername() != null ? r.getWorkerUsername() : "—")
                .paymentMethod(r.getPaymentMethod() != null
                        ? ("CASH".equals(r.getPaymentMethod()) ? "Efectivo" : "Tarjeta")
                        : "—")
                .amount(r.getTotalRefunded())
                .ticketUrl("/admin/return/" + r.getId())
                .build()).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", sliceData.getNumber());
        response.put("hasNext", sliceData.hasNext());
        response.put("first", sliceData.isFirst());
        response.put("last", !sliceData.hasNext());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/return/{id}")
    public ResponseEntity<Resource> downloadReturnPdf(@PathVariable Long id) {
        Optional<SaleReturn> returnOpt = returnService.findById(id);
        if (returnOpt.isEmpty())
            return ResponseEntity.notFound().build();
        SaleReturn saleReturn = returnOpt.get();

        Context context = new Context();
        context.setVariable("saleReturn", saleReturn);
        context.setVariable("companySettings", companySettingsService.getSettings());
        context.setVariable("pdfMode", true);

        Sale originalSale = saleReturn.getOriginalSale();
        boolean applyRecargo = originalSale.isApplyRecargo();
        List<TaxBreakdown> standardBreakdowns = new ArrayList<>();

        for (ReturnLine line : saleReturn.getLines()) {
            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            standardBreakdowns.add(recargoCalculator.calculateLineBreakdown(
                    line.getSaleLine().getProduct().getId(), line.getSaleLine().getProduct().getName(),
                    line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo));
        }

        String template;
        if (saleReturn.getRectificativeInvoice() != null) {
            template = "tpv/rectificative-invoice";
            List<TaxBreakdown> negativeBreakdowns = standardBreakdowns.stream().map(bd -> TaxBreakdown.builder()
                    .productId(bd.getProductId()).productName(bd.getProductName()).unitPrice(bd.getUnitPrice())
                    .quantity(bd.getQuantity() != null ? bd.getQuantity().negate() : BigDecimal.ZERO)
                    .baseAmount(bd.getBaseAmount().negate()).vatRate(bd.getVatRate())
                    .vatAmount(bd.getVatAmount().negate()).recargoRate(bd.getRecargoRate())
                    .recargoAmount(bd.getRecargoAmount().negate())
                    .totalAmount(bd.getTotalAmount().negate()).recargoApplied(applyRecargo).build())
                    .collect(Collectors.toList());
            context.setVariable("taxBreakdowns", negativeBreakdowns);
            context.setVariable("totalBase", negativeBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalVat", negativeBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalRecargo", negativeBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

            List<Map<String, Object>> negativeLines = new ArrayList<>();
            for (ReturnLine line : saleReturn.getLines()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", line.getSaleLine().getProduct().getName());
                map.put("unitPrice", line.getUnitPrice());
                map.put("quantity", line.getQuantity().negate());
                map.put("subtotal", line.getSubtotal().negate());
                map.put("vatRate", line.getVatRate());
                map.put("recargoRate", line.getSaleLine().getRecargoRate());
                negativeLines.add(map);
            }
            context.setVariable("negativeLines", negativeLines);
            context.setVariable("totalAmount", saleReturn.getTotalRefunded().negate());
        } else {
            template = "tpv/return-receipt";
            context.setVariable("taxBreakdowns", standardBreakdowns);
            context.setVariable("totalBase", standardBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalVat", standardBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            context.setVariable("totalRecargo", standardBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        }
        context.setVariable("applyRecargo", applyRecargo);

        byte[] pdfBytes = generatePdfFromTemplate(template, context);
        String filename = "Devolucion_" + saleReturn.getReturnNumber() + ".pdf";
        return createPdfResponse(pdfBytes, filename);
    }

    @GetMapping("/tariffs/{id}/history/pdf")
    public ResponseEntity<Resource> downloadTariffPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
        Tariff tariff = tariffService.findById(id).orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalTime targetTime = time != null ? time : LocalTime.now();

        List<TariffPriceEntryDTO> history = tariffPriceHistoryService.getPricesForTariffAtExactDateTimeList(id,
                targetDate, targetTime);
        byte[] pdfData = pdfReportService.generateTariffSheet(tariff, history, targetDate);
        String filename = String.format("Tarifa_%s_%s_%s.pdf", tariff.getName(), targetDate,
                targetTime.toString().replace(":", "-"));
        return createPdfResponse(pdfData, filename);
    }

    private byte[] generatePdfFromTemplate(String template, Context context) {
        try {
            String htmlContent = templateEngine.process(template, context);
            htmlContent = cleanHtmlForPdf(htmlContent);
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/static/");
                builder.toStream(os);
                builder.run();
                return os.toByteArray();
            }
        } catch (Exception e) {
            log.error("Error generating PDF from template: " + template, e);
            throw new RuntimeException("Error generating PDF: " + e.getMessage(), e);
        }
    }

    private String cleanHtmlForPdf(String html) {
        if (html == null) return "";
        String cleaned = html.replaceAll("<(meta|br|hr|img|input|link)([^>]*?)(?<!/)>", "<$1$2 />");
        return cleaned.replace("&middot;", "&#183;")
                .replace("&copy;", "&#169;")
                .replace("&reg;", "&#174;")
                .replace("&trade;", "&#8482;")
                .replace("&nbsp;", "&#160;")
                .replace("&euro;", "&#8364;")
                .replace("&ordm;", "&#186;")
                .replace("&ordf;", "&#170;")
                .replace("&mdash;", "&#8212;")
                .replace("&ndash;", "&#8211;")
                .replace("&aacute;", "&#225;")
                .replace("&eacute;", "&#233;")
                .replace("&iacute;", "&#237;")
                .replace("&oacute;", "&#243;")
                .replace("&uacute;", "&#250;")
                .replace("&ntilde;", "&#241;")
                .replace("&iquest;", "&#191;")
                .replace("&iexcl;", "&#161;")
                .replaceAll("&(?!(?:[a-zA-Z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);)", "&amp;");
    }

    private ResponseEntity<Resource> createPdfResponse(byte[] pdfBytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdfBytes));
    }
}
