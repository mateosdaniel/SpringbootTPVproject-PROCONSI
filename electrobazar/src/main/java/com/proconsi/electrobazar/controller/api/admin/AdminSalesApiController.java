package com.proconsi.electrobazar.controller.api.admin;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.dto.AdminSaleListingDTO;
import com.proconsi.electrobazar.dto.AdminSaleProjection;
import com.proconsi.electrobazar.dto.TaxBreakdown;
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
import java.util.*;

/**
 * REST Controller for managing sales and downloading invoices/tickets.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminSalesApiController {

    private final SaleService saleService;
    private final InvoiceService invoiceService;
    private final TicketService ticketService;
    private final CompanySettingsService companySettingsService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final TemplateEngine templateEngine;

    @GetMapping("/sales")
    public ResponseEntity<Map<String, Object>> getSalesPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Set<String> allowedSort = Set.of("createdAt", "totalAmount", "id");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        final boolean isSearching = search != null && !search.trim().isEmpty();
        int finalSize = isSearching ? 15 : size;

        Pageable pageable = PageRequest.of(page, finalSize, Sort.by(direction, safeSort));
        
        long t0 = System.currentTimeMillis();
        List<AdminSaleListingDTO> list;
        boolean hasNext;
        boolean first;

        if (!isSearching && (type == null || type.isBlank()) && (method == null || method.isBlank()) && date == null) {
            org.springframework.data.domain.Slice<AdminSaleProjection> projectionSlice = saleService.findAdminListing(pageable);
            list = projectionSlice.getContent().stream().map(p -> AdminSaleListingDTO.builder()
                    .id(p.getId())
                    .createdAt(p.getCreatedAt())
                    .totalAmount(p.getTotalAmount())
                    .paymentMethod(p.getPaymentMethod())
                    .status(p.getStatus())
                    .customerName(p.getCustomerName())
                    .customerTaxId(p.getCustomerTaxId())
                    .workerUsername(p.getWorkerUsername())
                    .displayId(p.getDisplayId())
                    .type(p.getType())
                    .build()).toList();
            hasNext = projectionSlice.hasNext();
            first = projectionSlice.isFirst();
            log.info("[PERF] getSalesPage (OPTIMIZED SLICE) took {}ms", System.currentTimeMillis() - t0);
        } else {
            org.springframework.data.domain.Slice<Sale> slice = saleService.searchSlice(search, type, method, date, pageable);
            list = slice.getContent().stream().map(this::mapToDTO).toList();
            hasNext = slice.hasNext();
            first = slice.isFirst();
            log.info("[PERF] getSalesPage (FILTER SLICE) took {}ms", System.currentTimeMillis() - t0);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", page);
        response.put("hasNext", hasNext);
        response.put("first", first);
        response.put("last", !hasNext);
        return ResponseEntity.ok(response);
    }

    private AdminSaleListingDTO mapToDTO(Sale s) {
        return AdminSaleListingDTO.builder()
                .id(s.getId())
                .createdAt(s.getCreatedAt())
                .totalAmount(s.getTotalAmount())
                .paymentMethod(s.getPaymentMethod() != null ? s.getPaymentMethod().name() : null)
                .status(s.getStatus() != null ? s.getStatus().name() : "ACTIVE")
                .customerName(s.getCustomer() != null ? s.getCustomer().getName() : null)
                .customerTaxId(s.getCustomer() != null ? s.getCustomer().getTaxId() : null)
                .workerUsername(s.getWorker() != null ? s.getWorker().getUsername() : null)
                .displayId(s.getInvoice() != null ? s.getInvoice().getInvoiceNumber()
                        : (s.getTicket() != null ? s.getTicket().getTicketNumber() : "#" + s.getId()))
                .type((s.getInvoice() != null || s.getTipoDocumento() == TipoDocumento.FACTURA_COMPLETA) ? "factura" : "ticket")
                .build();
    }

    @GetMapping("/download/invoice/{id}")
    public ResponseEntity<Resource> downloadInvoicePdf(@PathVariable Long id) {
        Sale sale = saleService.findById(id);
        if (sale == null)
            return ResponseEntity.notFound().build();

        Context context = new Context();
        context.setVariable("sale", sale);
        context.setVariable("companySettings", companySettingsService.getSettings());
        context.setVariable("pdfMode", true);

        Optional<Invoice> invoiceOpt = invoiceService.findBySaleId(id);
        invoiceOpt.ifPresent(inv -> context.setVariable("invoice", inv));

        if (invoiceOpt.isEmpty()) {
            ticketService.findBySaleId(id).ifPresent(t -> context.setVariable("ticket", t));
        }

        boolean applyRecargo = sale.getCustomer() != null
                && Boolean.TRUE.equals(sale.getCustomer().getHasRecargoEquivalencia());
        List<TaxBreakdown> breakdowns = new ArrayList<>();
        for (SaleLine line : sale.getLines()) {
            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            breakdowns.add(recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(), line.getProduct().getName(),
                    line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo));
        }
        context.setVariable("taxBreakdowns", breakdowns);
        context.setVariable("applyRecargo", applyRecargo);
        context.setVariable("totalBase", breakdowns.stream().map(TaxBreakdown::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        context.setVariable("totalVat", breakdowns.stream().map(TaxBreakdown::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        context.setVariable("totalRecargo", breakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

        String template = invoiceOpt.isPresent() ? "tpv/invoice" : "tpv/receipt";
        byte[] pdfBytes = generatePdfFromTemplate(template, context);

        String filename = (invoiceOpt.isPresent() ? "Factura_" + invoiceOpt.get().getInvoiceNumber() : "Ticket_" + id)
                + ".pdf";
        return createPdfResponse(pdfBytes, filename);
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
