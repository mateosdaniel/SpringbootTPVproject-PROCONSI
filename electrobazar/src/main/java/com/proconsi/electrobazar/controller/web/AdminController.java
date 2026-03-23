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
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

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
    private final ActivityLogService activityLogService;

    /**
     * Renders the product and category management view.
     */
    @GetMapping("/productos-categorias")
    public String productsCategories(
            @RequestParam(required = false, defaultValue = "productsView") String returnView,
            Model model,
            HttpSession session) {
        // Allow access if worker has specific permission or is full admin
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        boolean hasPermission = worker.getEffectivePermissions().contains("MANAGE_PRODUCTS_TPV") ||
                worker.getEffectivePermissions().contains("ADMIN_ACCESS");

        if (!hasPermission)
            return "redirect:/tpv";

        model.addAttribute("products", productService.findAllWithCategory());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("returnView", returnView);
        return "admin/productos-categorias";
    }

    /**
     * Renders the main administration dashboard.
     */
    @GetMapping("/admin")
    public String index(
            @RequestParam(required = false) String view,
            Model model,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/tpv";
        }

        model.addAttribute("activeView", view);

        // Load all data required by the various Admin views
        // Dashboard
        model.addAttribute("products", productService.findAllWithCategory());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("sales",
                saleService.findBetween(LocalDateTime.now().minusDays(30), LocalDateTime.now()));
        model.addAttribute("cashRegisters", cashRegisterService.findAllClosed());
        model.addAttribute("workers", workerService.findAll());
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("returns", returnService.findByCreatedAtBetween(LocalDateTime.now().minusDays(30),
                LocalDateTime.now()));
        model.addAttribute("tariffs", tariffService.findAll());
        model.addAttribute("tariffCustomerCounts", tariffService.getCustomerCountPerTariff());
        model.addAttribute("taxRates", taxRateRepository.findAll());
        model.addAttribute("futureTaxRates", taxRateRepository.findByValidFromAfter(LocalDate.now()));
        model.addAttribute("companySettings", companySettingsService.getSettings());

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
            activityLogService.logActivity("DEACTIVATE_WORKER", "Worker deactivated: " + w.getUsername(), "Admin",
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
            activityLogService.logActivity("IMPORT_CSV", "CSV Import successful: " + result, "Admin", "IMPORT", null);
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
            activityLogService.logActivity("IMPORT_CUSTOMERS_CSV", "CSV import clientes: " + result, "Admin", "IMPORT", null);
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
            com.proconsi.electrobazar.model.CashRegister register = cashRegisterService.findById(id);
            if (register == null)
                return ResponseEntity.notFound().build();

            // Regenerate on demand
            byte[] pdfData = pdfReportService.generateCashCloseReport(register);
            String dateStr = register.getClosedAt() != null
                    ? register.getClosedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
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
            @RequestParam(required = false, defaultValue = "tarifasView") String returnView,
            Model model,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/tpv";
        }
        Tariff tariff = tariffService.findById(id)
                .orElseThrow(() -> new RuntimeException("Tariff not found"));

        LocalDate targetDate = date;
        List<LocalDate> availableDates = tariffPriceHistoryService.getDistinctValidFromDates(id);
        
        // Add TODAY to the list of relevant dates if it has active prices and is not already there
        LocalDate today = LocalDate.now();
        if (!availableDates.contains(today)) {
             // We don't add it to the DB but to the list shown in the UI
             availableDates.add(0, today); 
        }

        // Default to most recent if no date provided
        if (targetDate == null && !availableDates.isEmpty()) {
            targetDate = availableDates.get(0);
        } else if (targetDate == null) {
            targetDate = today;
        }

        List<com.proconsi.electrobazar.dto.TariffPriceEntryDTO> history = tariffPriceHistoryService.getPricesForTariffAtDate(id, targetDate);
        boolean dateExists = !history.isEmpty();

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
            if (index > 0) nextDate = availableDates.get(index - 1);
            if (index < availableDates.size() - 1) prevDate = availableDates.get(index + 1);
            
            // Si el "día anterior real" no es la fecha de transición anterior, permitimos ir un día atrás
            if (prevDate == null || !prevDate.equals(targetDate.minusDays(1))) {
                 LocalDate dayBefore = targetDate.minusDays(1);
                 // Solo si el día antes tiene historia (está después o es igual a la primera transición)
                 if (!availableDates.isEmpty() && !dayBefore.isBefore(availableDates.get(availableDates.size()-1))) {
                     prevDate = dayBefore;
                 }
            }
        } else {
            // Estamos navegando en un día "hueco" (sin cambios de precio)
            nextDate = targetDate.plusDays(1);
            prevDate = targetDate.minusDays(1);
            
            // No podemos ir más allá del primer registro histórico
            if (!availableDates.isEmpty() && prevDate.isBefore(availableDates.get(availableDates.size()-1))) {
                prevDate = null;
            }
        }

        model.addAttribute("tariff", tariff);
        model.addAttribute("history", history);
        model.addAttribute("selectedDate", targetDate);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute("prevDate", prevDate);
        model.addAttribute("nextDate", nextDate);
        model.addAttribute("firstDate", availableDates.isEmpty() ? null : availableDates.get(availableDates.size() - 1));
        model.addAttribute("lastDate", availableDates.isEmpty() ? null : availableDates.get(0));
        model.addAttribute("dateExists", dateExists);
        model.addAttribute("returnView", returnView);

        return "admin/tariff-price-history";
    }

    @GetMapping("/admin/tariffs/{id}/history/pdf")
    @ResponseBody
    public ResponseEntity<?> downloadTariffPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).build();
        }

        try {
            Tariff tariff = tariffService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Tariff not found"));

            LocalDate targetDate = date != null ? date : LocalDate.now();
            List<com.proconsi.electrobazar.dto.TariffPriceEntryDTO> history = tariffPriceHistoryService
                    .getPricesForTariffAtDate(id, targetDate);

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

    @PostMapping("/admin/settings")
    public String saveSettings(
            @ModelAttribute com.proconsi.electrobazar.model.CompanySettings companySettings,
            HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/login";
        }
        companySettingsService.save(companySettings);
        redirectAttributes.addFlashAttribute("successMessage", "Company settings updated successfully.");
        return "redirect:/admin?view=settingsView";
    }
}
