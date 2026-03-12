package com.proconsi.electrobazar.controller.api;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiRestController {

    private final AdminPinService adminPinService;
    private final ProductService productService;
    private final CsvImportService csvImportService;
    private final SaleService saleService;
    private final CashRegisterService cashRegisterService;
    private final PdfReportService pdfReportService;
    private final WorkerService workerService;
    private final CompanySettingsService companySettingsService;
    private final InvoiceService invoiceService;
    private final TicketService ticketService;
    private final ReturnService returnService;
    private final TariffService tariffService;
    private final TariffPriceHistoryService tariffPriceHistoryService;
    private final ActivityLogService activityLogService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final TemplateEngine templateEngine;

    @PostMapping("/verify-pin")
    public ResponseEntity<?> verifyPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (adminPinService.verifyPin(pin)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "PIN incorrecto"));
        }
    }

    @GetMapping("/download/invoice/{id}")
    public ResponseEntity<Resource> downloadInvoicePdf(@PathVariable Long id) {
        Sale sale = saleService.findById(id);
        if (sale == null)
            return ResponseEntity.notFound().build();

        Context context = new Context();
        context.setVariable("sale", sale);
        context.setVariable("companySettings", companySettingsService.getSettings());

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

    @GetMapping("/download/return/{id}")
    public ResponseEntity<Resource> downloadReturnPdf(@PathVariable Long id) {
        Optional<SaleReturn> returnOpt = returnService.findById(id);
        if (returnOpt.isEmpty())
            return ResponseEntity.notFound().build();
        SaleReturn saleReturn = returnOpt.get();

        Context context = new Context();
        context.setVariable("saleReturn", saleReturn);
        context.setVariable("companySettings", companySettingsService.getSettings());

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
                    .quantity(bd.getQuantity() * -1).baseAmount(bd.getBaseAmount().negate()).vatRate(bd.getVatRate())
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
                map.put("quantity", line.getQuantity() * -1);
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Tariff tariff = tariffService.findById(id).orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));
        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<TariffPriceEntryDTO> history = tariffPriceHistoryService.getPricesForTariffAtDate(id, targetDate);
        byte[] pdfData = pdfReportService.generateTariffSheet(tariff, history);
        String filename = String.format("Tarifa_%s_%s.pdf", tariff.getName(), targetDate);
        return createPdfResponse(pdfData, filename);
    }

    @PostMapping("/upload-csv")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file) {
        try {
            String result = csvImportService.importProductsCsv(file);
            activityLogService.logActivity("IMPORTAR_CSV", "Importación CSV realizada: " + result, "Admin", "IMPORT",
                    null);
            return ResponseEntity.ok(Map.of("ok", true, "message", result));
        } catch (Exception e) {
            log.error("Error processing CSV upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "message", "Error al procesar: " + e.getMessage()));
        }
    }

    @PostMapping("/tax-rates/{newId}/apply-to-products")
    public ResponseEntity<?> applyNewTaxRate(@PathVariable Long newId) {
        try {
            productService.applyNewTaxRate(newId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody CompanySettings companySettings) {
        companySettingsService.save(companySettings);
        return ResponseEntity.ok(Map.of("message", "Configuración de empresa actualizada correctamente."));
    }

    @DeleteMapping("/workers/{id}")
    public ResponseEntity<?> deleteWorker(@PathVariable Long id) {
        workerService.findById(id).ifPresent(w -> {
            w.setActive(false);
            workerService.save(w);
            activityLogService.logActivity("DESACTIVAR_TRABAJADOR", "Trabajador desactivado: " + w.getUsername(),
                    "Admin", "WORKER", id);
        });
        return ResponseEntity.ok().build();
    }

    private byte[] generatePdfFromTemplate(String template, Context context) {
        try {
            String htmlContent = templateEngine.process(template, context);
            htmlContent = cleanHtmlForPdf(htmlContent);
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(htmlContent, "classpath:/templates/");
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
        if (html == null)
            return "";
        String cleaned = html.replaceAll("<(meta|br|hr|img|input|link)([^>]*?)(?<!/)>", "<$1$2 />");
        return cleaned.replace("&copy;", "&#169;")
                .replace("&reg;", "&#174;")
                .replace("&trade;", "&#8482;")
                .replace("&nbsp;", "&#160;")
                .replace("&euro;", "&#8364;");
    }

    private ResponseEntity<Resource> createPdfResponse(byte[] pdfBytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(pdfBytes));
    }
}
