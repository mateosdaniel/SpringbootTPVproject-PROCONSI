package com.proconsi.electrobazar.controller.api;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.proconsi.electrobazar.dto.*;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import com.proconsi.electrobazar.util.AesEncryptionUtil;
import com.proconsi.electrobazar.repository.AppSettingRepository;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST Controller for administrative and management operations.
 * Handles sensitive features such as PDF generation, stats, company
 * configuration,
 * CSV imports, and worker management.
 */
@Slf4j
@RestController
@RequestMapping({"/api/admin", "/admin/api"})
@RequiredArgsConstructor
public class AdminApiRestController {

    private final AdminPinService adminPinService;
    private final AppSettingRepository appSettingRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
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

    /**
     * Retrieves aggregated statistics for the management dashboard.
     * 
     * @param period Time period (e.g., "today", "week", "month").
     * @return Dashboard statistics data.
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(cashRegisterService.getDashboardStats(period));
    }

    /**
     * Verifies the super-admin master PIN before performing sensitive actions.
     * 
     * @param body Payload containing the "pin" string.
     * @return 200 OK if valid, 401 Unauthorized otherwise.
     */
    @PostMapping("/verify-pin")
    public ResponseEntity<?> verifyPin(@RequestBody Map<String, String> body) {
        String pin = body.get("pin");
        if (adminPinService.verifyPin(pin)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "PIN incorrecto"));
        }
    }

    /**
     * Generates and downloads the official PDF document for an existing sale
     * (Invoice or Ticket).
     * 
     * @param id The sale ID.
     * @return PDF binary resource tagged with appropriate filename.
     */
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

    /**
     * Generates and downloads the PDF receipt or rectificative invoice for a
     * return.
     * 
     * @param id The return ID.
     * @return PDF binary resource.
     */
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

    /**
     * Exports a specific Tariff price list to PDF.
     * 
     * @param id   Tariff ID.
     * @param date The date for which prices should be calculated (defaults to now).
     * @return PDF resource.
     */
    @GetMapping("/tariffs/{id}/history/pdf")
    public ResponseEntity<Resource> downloadTariffPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Tariff tariff = tariffService.findById(id).orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));
        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<TariffPriceEntryDTO> history = tariffPriceHistoryService.getPricesForTariffAtDate(id, targetDate);
        byte[] pdfData = pdfReportService.generateTariffSheet(tariff, history, targetDate);
        String filename = String.format("Tarifa_%s_%s.pdf", tariff.getName(), targetDate);
        return createPdfResponse(pdfData, filename);
    }

    /**
     * Bulk imports products from a CSV file.
     * 
     * @param file Multipat CSV file.
     * @return Import result summary.
     */
    @PostMapping("/upload-csv")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file) throws Exception {
        String result = csvImportService.importProductsCsv(file);
        activityLogService.logActivity("IMPORTAR_CSV", "Importación CSV realizada: " + result, "Admin", "IMPORT", null);
        return ResponseEntity.ok(Map.of("ok", true, "message", result));
    }

    /**
     * Manually triggers a tax rate transition across the product catalog.
     * 
     * @param newId ID of the new Tax Rate to apply.
     * @return 200 OK.
     */
    @PostMapping("/tax-rates/{newId}/apply-to-products")
    public ResponseEntity<?> applyNewTaxRate(@PathVariable Long newId) {
        productService.applyNewTaxRate(newId);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves the master company settings (name, CIF, address, legal text).
     * 
     * @return {@link CompanySettings} entity.
     */
    @GetMapping("/settings")
    public ResponseEntity<CompanySettings> getSettings() {
        return ResponseEntity.ok(companySettingsService.getSettings());
    }

    /**
     * Updates the master company configuration.
     * 
     * @param companySettings New settings data.
     * @return Success message.
     */
    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody CompanySettings companySettings) {
        companySettingsService.save(companySettings);
        return ResponseEntity.ok(Map.of("message", "Configuración de empresa actualizada correctamente."));
    }

    /**
     * Retrieves the current mail settings (SMTP).
     * @return Map of mail configuration.
     */
    @GetMapping("/mail-settings")
    public ResponseEntity<Map<String, String>> getMailSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("host", appSettingRepository.findByKey("mail.host").map(AppSetting::getValue).orElse(""));
        settings.put("port", appSettingRepository.findByKey("mail.port").map(AppSetting::getValue).orElse("587"));
        settings.put("username", appSettingRepository.findByKey("mail.username").map(AppSetting::getValue).orElse(""));
        settings.put("password", appSettingRepository.findByKey("mail.password").isPresent() ? "••••••••" : "");
        return ResponseEntity.ok(settings);
    }

    /**
     * Updates mail settings (SMTP) and encrypts the password.
     * @param body Payload with host, port, username and password.
     * @return 200 OK.
     */
    @PostMapping("/mail-settings")
    public ResponseEntity<?> saveMailSettings(@RequestBody Map<String, String> body) {
        if (body.get("host") != null) saveAppSetting("mail.host", body.get("host"));
        if (body.get("port") != null) saveAppSetting("mail.port", body.get("port"));
        if (body.get("username") != null) saveAppSetting("mail.username", body.get("username"));
        if (body.get("password") != null && !body.get("password").isBlank() && !body.get("password").equals("••••••••")) {
            saveAppSetting("mail.password", aesEncryptionUtil.encrypt(body.get("password")));
        }
        return ResponseEntity.ok(Map.of("message", "Configuración guardada correctamente"));
    }

    /**
     * Updates the admin PIN securely.
     * @param body Payload with currentPin and newPin.
     * @return 200 OK or error message.
     */
    @PostMapping("/update-pin")
    public ResponseEntity<?> updatePin(@RequestBody Map<String, String> body) {
        try {
            adminPinService.updatePin(body.get("currentPin"), body.get("newPin"));
            return ResponseEntity.ok(Map.of("message", "PIN de administrador actualizado correctamente."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private void saveAppSetting(String key, String value) {
        AppSetting setting = appSettingRepository.findByKey(key)
                .orElse(AppSetting.builder().key(key).build());
        setting.setValue(value);
        appSettingRepository.save(setting);
    }

    /**
     * Deactivates a worker account (Soft Delete).
     * 
     * @param id Worker ID.
     * @return 200 OK.
     */
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
        // Replace unclosed tags common in HTML but invalid in strict XML/XHTML
        String cleaned = html.replaceAll("<(meta|br|hr|img|input|link)([^>]*?)(?<!/)>", "<$1$2 />");
        return cleaned.replace("&middot;", "&#183;")
                .replace("&copy;", "&#169;")
                .replace("&reg;", "&#174;")
                .replace("&trade;", "&#8482;")
                .replace("&nbsp;", "&#160;")
                .replace("&euro;", "&#8364;")
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
