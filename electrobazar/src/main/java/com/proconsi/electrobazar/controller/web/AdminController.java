package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import com.proconsi.electrobazar.model.Tariff;
import java.util.Map;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Sale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;

/**
 * Main controller for administrative views and operations.
 * Handles the dashboard, product management, worker management, and reports.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CsvImportService csvImportService;
    private final SaleService saleService;
    private final CashRegisterService cashRegisterService;
    private final PdfReportService pdfReportService;
    private final WorkerService workerService;
    private final CustomerService customerService;
    private final RoleService roleService;
    private final ReturnService returnService;
    private final TariffService tariffService;
    private final TaxRateRepository taxRateRepository;
    private final TariffPriceHistoryService tariffPriceHistoryService;
    private final CompanySettingsService companySettingsService;
    private final CouponService couponService;
    private final ActivityLogService activityLogService;
    private final BackupService backupService;
    private final PromotionService promotionService;
    private final MeasurementUnitService measurementUnitService;

    /**
     * Endpoint to execute a manual backup on demand.
     */
    @PostMapping("/admin/backup/now")
    @ResponseBody
    public ResponseEntity<?> manualBackup(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }
        Worker admin = (Worker) session.getAttribute("worker");
        BackupService.BackupResult result = backupService.performBackup("MANUAL",
                admin != null ? admin.getUsername() : "Admin");
        return ResponseEntity.ok(result);
    }

    /**
     * Renders the product and category management view.
     */
    @GetMapping("/productos-categorias")
    public String productsCategories(
            @RequestParam(required = false, defaultValue = "productsView") String returnView,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "0") int categoriesPage,
            @RequestParam(defaultValue = "25") int categoriesSize,
            Model model,
            HttpSession session) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return "redirect:/login";
        }

        Boolean isAdmin = (Boolean) session.getAttribute("admin");
        if (isAdmin == null || !isAdmin) {
            return "redirect:/tpv";
        }

        Page<Product> productsPaged = productService.findAllWithCategoryPaged(page, size);
        Page<Category> catsPaged = categoryService.getFilteredCategories(null,
                PageRequest.of(categoriesPage, categoriesSize, Sort.by("nameEs").ascending()));

        model.addAttribute("products", productsPaged.getContent());
        model.addAttribute("productsPage", productsPaged);

        model.addAttribute("categories", catsPaged.getContent());
        model.addAttribute("categoriesPage", catsPaged);
        model.addAttribute("allCategories", categoryService.findAllActive());
        model.addAttribute("measurementUnits", measurementUnitService.findAll());
        model.addAttribute("taxRates", taxRateRepository.findAll());

        model.addAttribute("returnView", returnView);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("categoriesPageNumber", categoriesPage);
        model.addAttribute("categoriesPageSize", categoriesSize);

        model.addAttribute("companySettings", companySettingsService.getSettings());

        return "admin/productos-categorias";
    }

    /**
     * Renders the main administration dashboard.
     */
    @GetMapping("/admin")
    public String index(
            @RequestParam(required = false) String view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "0") int categoriesPage,
            @RequestParam(defaultValue = "50") int categoriesSize,
            Model model,
            HttpSession session) {
        long t0 = System.currentTimeMillis();

        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/tpv";
        }
        log.info("Dashboard - Auth check: {}ms", System.currentTimeMillis() - t0);

        model.addAttribute("activeView", view);

        // Essential data for first paint (fast delivery)
        model.addAttribute("companySettings", companySettingsService.getSettings());
        log.info("Dashboard - Settings: {}ms", System.currentTimeMillis() - t0);

        model.addAttribute("futureTaxRates", taxRateRepository.findByValidFromAfter(LocalDate.now()));
        log.info("Dashboard - Future Tax Rates: {}ms", System.currentTimeMillis() - t0);

        model.addAttribute("taxRates", List.of()); // Sub-views will fetch these

        // Minimal metadata for pagination
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("categoriesPageNumber", categoriesPage);
        model.addAttribute("categoriesPageSize", categoriesSize);

        // Pre-populate with essential lightweight data to satisfy initial view
        // rendering
        model.addAttribute("taxRates", taxRateRepository.findAll());
        model.addAttribute("allCategories", categoryService.findAllActive());

        // Limited top products moved to AJAX to improve LCP
        model.addAttribute("products", List.of());
        model.addAttribute("tariffs", tariffService.findAll());
        model.addAttribute("tariffCustomerCounts", Map.of());

        model.addAttribute("workers", workerService.findAll());
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("measurementUnits", measurementUnitService.findAll());
        model.addAttribute("coupons", List.of()); // AJAX load
        model.addAttribute("promotions", List.of()); // AJAX load

        // Empty heavy datasets to keep index load <100ms
        model.addAttribute("sales", List.of());
        model.addAttribute("salesTotalPages", 0);
        model.addAttribute("returns", List.of());
        model.addAttribute("cashRegisters", List.of());
        model.addAttribute("activeRegister", cashRegisterService.getOpenRegister());
        model.addAttribute("customers", List.of());
        model.addAttribute("productsPage", Page.empty());
        model.addAttribute("categoriesPage", Page.empty());

        model.addAttribute("isOptimizedView", true);
        log.info("Dashboard - Total setup: {}ms", System.currentTimeMillis() - t0);

        return "admin/admin";
    }

    @GetMapping("/api/permissions")
    @ResponseBody
    public List<String> getAllPermissions() {
        return roleService.findAllPermissions();
    }

    @PostMapping("/admin/workers/save")
    @ResponseBody
    public ResponseEntity<?> saveWorker(
            @RequestBody Worker worker,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }
        workerService.save(worker);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/workers/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteWorker(@PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }
        // Instead of hard delete, we deactivate the worker to preserve history
        workerService.findById(id).ifPresent(w -> {
            w.setActive(false);
            workerService.save(w);
            activityLogService.logActivity("DEACTIVATE_WORKER", "Trabajador desactivado: " + w.getUsername(), "Admin",
                    "WORKER", id);
        });
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/products/{id}/hard")
    @ResponseBody
    public ResponseEntity<?> deleteProductHard(@PathVariable Long id, HttpSession session) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return ResponseEntity.status(401).build();
        }
        boolean hasPermission = worker.getEffectivePermissions().contains("MANAGE_PRODUCTS_TPV") ||
                worker.getEffectivePermissions().contains("ADMIN_ACCESS");
        if (!hasPermission) {
            return ResponseEntity.status(403).build();
        }
        productService.hardDeleteProduct(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/admin/upload-csv")
    @ResponseBody
    public ResponseEntity<?> uploadCsv(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401)
                    .body(Map.of("ok", false, "message", "Unauthorized"));
        }

        try {
            String result = csvImportService.importProductsCsv(file);
            activityLogService.logActivity("IMPORT_CSV", "Importación de CSV exitosa: " + result, "Admin", "IMPORT",
                    null);
            return ResponseEntity.ok(Map.of("ok", true, "message", result));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("ok", false, "message", "Error processing file: " + e.getMessage()));
        }
    }

    @PostMapping("/admin/upload-customers-csv")
    @ResponseBody
    public ResponseEntity<?> uploadCustomersCsv(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401)
                    .body(Map.of("ok", false, "message", "Unauthorized"));
        }

        try {
            String result = csvImportService.importCustomersCsv(file);
            activityLogService.logActivity("IMPORT_CUSTOMERS_CSV", "CSV import clientes: " + result, "Admin", "IMPORT",
                    null);
            return ResponseEntity.ok(Map.of("ok", true, "message", result));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("ok", false, "message", "Error al procesar el CSV: " + e.getMessage()));
        }
    }

    @PostMapping("/admin/sales/cancel/{id}")
    @ResponseBody
    public ResponseEntity<?> cancelSale(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }
        String reason = body.getOrDefault("reason", "Cancellation from administration");
        Worker adminWorker = (Worker) session.getAttribute("worker");

        try {
            saleService.cancelSale(id, adminWorker, reason);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/download/invoice/{id}")
    @Transactional(readOnly = true)
    public String downloadInvoicePdf(@PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }
        return "redirect:/tpv/receipt/" + id + "?autoPrint=true";
    }

    @GetMapping("/admin/download/return/{id}")
    @Transactional(readOnly = true)
    public String downloadReturnPdf(@PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }
        return "redirect:/tpv/return-receipt/" + id + "?autoPrint=true";
    }

    @GetMapping("/admin/download/cash-close/{id}")
    public ResponseEntity<?> downloadCashClosePdf(
            @PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.CashRegister cs = cashRegisterService.findById(id);
            if (cs == null)
                return ResponseEntity.notFound().build();

            // Regenerate on demand
            byte[] pdfData = pdfReportService.generateCashCloseReport(cs);
            String dateStr = cs.getClosedAt() != null
                    ? cs.getClosedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    : "UnknownDate";
            String filename = String.format("CashClose_%s_ID%d.pdf", dateStr, id);

            Resource resource = new ByteArrayResource(pdfData);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error generating cash close PDF for ID " + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/admin/tax-rates/{newId}/apply-to-products")
    @ResponseBody
    public ResponseEntity<?> applyNewTaxRate(@PathVariable Long newId, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }
        try {
            productService.applyNewTaxRate(newId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/tariffs/{id}/history")
    public String tariffHistory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam(required = false, defaultValue = "tarifasView") String returnView,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            Model model,
            HttpSession session) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null || !Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }

        Tariff tariff = tariffService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarifa no encontrada"));
        List<LocalDate> availableDates = tariffPriceHistoryService.getDistinctValidFromDates(id);

        LocalDate today = LocalDate.now();
        if (!availableDates.contains(today)) {
            availableDates.add(0, today);
        }
        LocalDate targetDate = date != null ? date : (availableDates.isEmpty() ? today : availableDates.get(0));

        List<LocalTime> dayVersions = tariffPriceHistoryService.getVersionsForDate(id, targetDate);

        boolean needsVersionSelection = false;
        LocalTime selectedTime = time;

        // 2. Aplicar lógica de pasarela (Gatekeeper)
        if (dayVersions.size() > 1 && selectedTime == null) {
            needsVersionSelection = true;
        } else if (selectedTime == null && !dayVersions.isEmpty()) {
            selectedTime = dayVersions.get(0); // Única versión disponible
        } else if (selectedTime == null) {
            selectedTime = LocalTime.MIN; // Caso de seguridad
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<com.proconsi.electrobazar.dto.TariffPriceEntryDTO> pricesPage;

        if (needsVersionSelection) {
            pricesPage = org.springframework.data.domain.Page.empty();
        } else if (time != null) {
            // Si el usuario seleccionó una HORA exacta, buscamos la versión que EMPEZÓ en
            // ese momento
            pricesPage = tariffPriceHistoryService.getPricesForTariffAtExactValidFrom(id, targetDate, selectedTime,
                    pageable);
        } else {
            // Si solo hay fecha, buscamos lo que estaba activo al final de ese día (o
            // ahora)
            pricesPage = tariffPriceHistoryService.getPricesForTariffAtExactDateTime(id, targetDate, selectedTime,
                    pageable);
        }

        boolean isInitializing = false;
        if (pricesPage.isEmpty() && !needsVersionSelection
                && tariffPriceHistoryService.isInitializationInProgress(id)) {
            isInitializing = true;
        }

        model.addAttribute("tariff", tariff);
        model.addAttribute("pricesPage", pricesPage);
        model.addAttribute("selectedDate", targetDate);
        model.addAttribute("selectedTime", selectedTime);
        model.addAttribute("dayVersions", dayVersions);
        model.addAttribute("needsVersionSelection", needsVersionSelection);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute("returnView", returnView);

        boolean dateExists = !pricesPage.isEmpty();

        LocalDate prevDate = null;
        LocalDate nextDate = null;

        // Find the index or position for navigation
        int index = -1;
        for (int i = 0; i < availableDates.size(); i++) {
            if (availableDates.get(i).equals(targetDate)) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            if (index > 0)
                nextDate = availableDates.get(index - 1);
            if (index < availableDates.size() - 1)
                prevDate = availableDates.get(index + 1);

            // Si el "día anterior real" no es la fecha de transición anterior, permitimos
            // ir un día atrás
            if (prevDate == null || !prevDate.equals(targetDate.minusDays(1))) {
                LocalDate dayBefore = targetDate.minusDays(1);
                // Solo si el día antes tiene historia (está después o es igual a la primera
                // transición)
                if (!availableDates.isEmpty() && !dayBefore.isBefore(availableDates.get(availableDates.size() - 1))) {
                    prevDate = dayBefore;
                }
            }
        } else {
            // Estamos navegando en un día "hueco" (sin cambios de precio)
            nextDate = targetDate.plusDays(1);
            prevDate = targetDate.minusDays(1);

            // No podemos ir más allá del primer registro histórico
            if (!availableDates.isEmpty() && prevDate.isBefore(availableDates.get(availableDates.size() - 1))) {
                prevDate = null;
            }
        }

        model.addAttribute("prevDate", prevDate);
        model.addAttribute("nextDate", nextDate);
        model.addAttribute("firstDate",
                availableDates.isEmpty() ? null : availableDates.get(availableDates.size() - 1));
        model.addAttribute("lastDate", availableDates.isEmpty() ? null : availableDates.get(0));
        model.addAttribute("dateExists", dateExists);
        model.addAttribute("isInitializing", isInitializing);

        return "admin/tariff-price-history";
    }

    @GetMapping("/admin/tariffs/{id}/history/pdf")
    @ResponseBody
    public ResponseEntity<?> downloadTariffPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }

        try {
            Tariff tariff = tariffService.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Tarifa no encontrada"));

            LocalDate targetDate = date != null ? date : LocalDate.now();
            LocalTime targetTime = time != null ? time : LocalTime.now();

            List<com.proconsi.electrobazar.dto.TariffPriceEntryDTO> history = tariffPriceHistoryService
                    .getPricesForTariffAtExactDateTimeList(id, targetDate, targetTime);

            byte[] pdfData = pdfReportService.generateTariffSheet(tariff, history, targetDate);
            String filename = String.format("Tariff_%s_%s.pdf", tariff.getName(), targetDate);

            Resource resource = new ByteArrayResource(pdfData);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error generating tariff PDF for ID " + id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/cash-register/{id}")
    public String cashRegisterDetail(@PathVariable Long id, Model model, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }
        com.proconsi.electrobazar.model.CashRegister register = cashRegisterService.findById(id);
        if (register == null) {
            return "redirect:/admin?view=cashCloseView";
        }

        LocalDateTime startTime = register.getOpeningTime();
        LocalDateTime endTime = register.getClosedAt() != null ? register.getClosedAt() : LocalDateTime.now();

        model.addAttribute("register", register);
        model.addAttribute("sales", saleService.findBetween(startTime, endTime));
        model.addAttribute("workerStats", saleService.getWorkerStatsBetween(startTime, endTime));
        model.addAttribute("companySettings", companySettingsService.getSettings());

        return "admin/cash-register-detail";
    }

    @GetMapping("/admin/sale/{id}")
    public String saleDetail(@PathVariable Long id, Model model, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }
        com.proconsi.electrobazar.model.Sale sale = saleService.findById(id);
        if (sale == null) {
            return "redirect:/admin?view=invoicesView";
        }

        model.addAttribute("sale", sale);
        model.addAttribute("companySettings", companySettingsService.getSettings());

        return "admin/sale-detail";
    }

    @GetMapping("/admin/return/{id}")
    public String returnDetail(@PathVariable Long id, Model model, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }
        com.proconsi.electrobazar.model.SaleReturn ret = returnService.findById(id).orElse(null);
        if (ret == null) {
            return "redirect:/admin?view=returnsHistoryView";
        }

        model.addAttribute("return", ret);
        model.addAttribute("companySettings", companySettingsService.getSettings());

        return "admin/return-detail";
    }

    @PostMapping("/admin/settings")
    public String saveSettings(
            @ModelAttribute com.proconsi.electrobazar.model.CompanySettings companySettings,
            HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }
        companySettingsService.save(companySettings);
        Worker admin = (Worker) session.getAttribute("worker");
        activityLogService.logFiscalEvent("CONFIG_CHANGE", "Modificación de la configuración fiscal de la empresa.",
                admin != null ? admin.getUsername() : "Admin");
        redirectAttributes.addFlashAttribute("successMessage", "Company settings updated successfully.");
        return "redirect:/admin?view=settingsView";
    }

    /**
     * Internal DTOs for Admin view to avoid Hibernate proxy issues and provide
     * typed access in Thymeleaf.
     */
    public static record AdminCategoryDTO(Long id, String name, String nameEs, Boolean active, String description) {
    }

    public static record AdminRoleDTO(Long id, String name) {
    }

    public static record AdminTariffDTO(Long id, String name, Boolean active, String description, String color,
            java.math.BigDecimal discountPercentage, Boolean systemTariff) {
        public String getDisplayLabel() {
            if (discountPercentage != null && discountPercentage.compareTo(java.math.BigDecimal.ZERO) > 0) {
                return name + " -" + discountPercentage.stripTrailingZeros().toPlainString() + "%";
            }
            return name;
        }
    }

    public static record AdminMeasurementUnitDTO(Long id, String name, String symbol, boolean active) {
    }

}
