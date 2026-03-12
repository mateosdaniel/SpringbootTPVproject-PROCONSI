package com.proconsi.electrobazar.controller.web;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.proconsi.electrobazar.service.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final com.proconsi.electrobazar.service.CsvImportService csvImportService;

    private final com.proconsi.electrobazar.service.SaleService saleService;
    private final com.proconsi.electrobazar.service.CashRegisterService cashRegisterService;
    private final com.proconsi.electrobazar.service.PdfReportService pdfReportService;
    private final com.proconsi.electrobazar.service.WorkerService workerService;
    private final com.proconsi.electrobazar.service.CustomerService customerService;
    private final com.proconsi.electrobazar.service.InvoiceService invoiceService;
    private final com.proconsi.electrobazar.service.RoleService roleService;
    private final com.proconsi.electrobazar.service.TicketService ticketService;
    private final com.proconsi.electrobazar.service.ReturnService returnService;
    private final com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator recargoCalculator;
    private final com.proconsi.electrobazar.service.TariffService tariffService;
    private final com.proconsi.electrobazar.repository.TaxRateRepository taxRateRepository;
    private final com.proconsi.electrobazar.service.TariffPriceHistoryService tariffPriceHistoryService;

    @GetMapping("/productos-categorias")
    public String productsCategories(
            @RequestParam(required = false, defaultValue = "productsView") String returnView,
            Model model,
            HttpSession session) {
        // Permitimos acceso si tiene el permiso específico O es admin total
        com.proconsi.electrobazar.model.Worker worker = (com.proconsi.electrobazar.model.Worker) session
                .getAttribute("worker");
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

    @GetMapping("/admin")
    public String index(
            @RequestParam(required = false) String view,
            Model model,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/tpv";
        }

        model.addAttribute("activeView", view);

        // Cargar todos los datos requeridos por las distintas vistas del Admin
        // Dashboard
        model.addAttribute("products", productService.findAllWithCategory());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("sales",
                saleService.findBetween(java.time.LocalDateTime.now().minusYears(1), java.time.LocalDateTime.now()));
        model.addAttribute("cashRegisters", cashRegisterService.findAllClosed());
        model.addAttribute("workers", workerService.findAll());
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("roles", roleService.findAll());
        model.addAttribute("returns", returnService.findByCreatedAtBetween(java.time.LocalDateTime.now().minusYears(1),
                java.time.LocalDateTime.now()));
        model.addAttribute("tariffs", tariffService.findAll());
        model.addAttribute("tariffCustomerCounts", tariffService.getCustomerCountPerTariff());
        model.addAttribute("taxRates", taxRateRepository.findAll());
        model.addAttribute("futureTaxRates", taxRateRepository.findByValidFromAfter(java.time.LocalDate.now()));

        return "admin/admin";
    }

    @GetMapping("/api/permissions")
    @ResponseBody
    public java.util.List<String> getAllPermissions() {
        return roleService.findAllPermissions();
    }

    @PostMapping("/admin/workers/save")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> saveWorker(
            @RequestBody com.proconsi.electrobazar.model.Worker worker,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        workerService.save(worker);
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/workers/delete/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteWorker(@PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        // Instead of hard delete, we deactivate the worker to preserve history
        workerService.findById(id).ifPresent(w -> {
            w.setActive(false);
            workerService.save(w);
        });
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/products/{id}/hard")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteProductHard(@PathVariable Long id, HttpSession session) {
        com.proconsi.electrobazar.model.Worker worker = (com.proconsi.electrobazar.model.Worker) session
                .getAttribute("worker");
        if (worker == null) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        boolean hasPermission = worker.getEffectivePermissions().contains("MANAGE_PRODUCTS_TPV") ||
                worker.getEffectivePermissions().contains("ADMIN_ACCESS");
        if (!hasPermission) {
            return org.springframework.http.ResponseEntity.status(403).build();
        }
        productService.hardDeleteProduct(id);
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @org.springframework.web.bind.annotation.PostMapping("/admin/upload-csv")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> uploadCsv(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401)
                    .body(java.util.Map.of("ok", false, "message", "No autorizado"));
        }

        try {
            String result = csvImportService.importProductsCsv(file);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("ok", true, "message", result));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.status(500)
                    .body(java.util.Map.of("ok", false, "message", "Error al procesar: " + e.getMessage()));
        }
    }

    @PostMapping("/admin/sales/cancel/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> cancelSale(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        String reason = body.getOrDefault("reason", "Anulación desde administración");
        com.proconsi.electrobazar.model.Worker adminWorker = (com.proconsi.electrobazar.model.Worker) session
                .getAttribute("worker");

        try {
            saleService.cancelSale(id, adminWorker, reason);
            return org.springframework.http.ResponseEntity.ok().build();
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
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
    public org.springframework.http.ResponseEntity<?> downloadCashClosePdf(
            @org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.CashRegister register = cashRegisterService.findById(id);
            if (register == null)
                return org.springframework.http.ResponseEntity.notFound().build();

            // Regenerate on demand
            byte[] pdfData = pdfReportService.generateCashCloseReport(register);
            String dateStr = register.getClosedAt() != null
                    ? register.getClosedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    : "UnknownDate";
            String filename = String.format("Cierre_Caja_%s_ID%d.pdf", dateStr, id);

            org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(pdfData);
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error generating cash close PDF for ID " + id, e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/admin/tax-rates/{newId}/apply-to-products")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> applyNewTaxRate(@PathVariable Long newId, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        try {
            productService.applyNewTaxRate(newId);
            return org.springframework.http.ResponseEntity.ok().build();
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/admin/tariffs/{id}/history")
    public String tariffHistory(
            @PathVariable Long id,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(required = false, defaultValue = "tarifasView") String returnView,
            Model model,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/tpv";
        }
        com.proconsi.electrobazar.model.Tariff tariff = tariffService.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));

        java.time.LocalDate targetDate = date;
        java.util.List<java.time.LocalDate> availableDates = tariffPriceHistoryService.getDistinctValidFromDates(id);
        
        // Default to most recent if no date provided
        if (targetDate == null && !availableDates.isEmpty()) {
            targetDate = availableDates.get(0);
        } else if (targetDate == null) {
            targetDate = java.time.LocalDate.now();
        }

        java.time.LocalDate prevDate = null;
        java.time.LocalDate nextDate = null;

        boolean dateExists = false;
        for (int i = 0; i < availableDates.size(); i++) {
            java.time.LocalDate d = availableDates.get(i);
            if (d.equals(targetDate)) {
                dateExists = true;
                if (i > 0)
                    nextDate = availableDates.get(i - 1);
                if (i < availableDates.size() - 1)
                    prevDate = availableDates.get(i + 1);
                break;
            }
        }

        model.addAttribute("tariff", tariff);
        model.addAttribute("history", tariffPriceHistoryService.getPricesForTariffAtDate(id, targetDate));
        model.addAttribute("selectedDate", targetDate);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute("prevDate", prevDate);
        model.addAttribute("nextDate", nextDate);
        model.addAttribute("firstDate", availableDates.isEmpty() ? null : availableDates.get(availableDates.size() - 1));
        model.addAttribute("lastDate", availableDates.isEmpty() ? null : availableDates.get(0));
        model.addAttribute("dateExists", dateExists || availableDates.isEmpty()); // If empty we don't show error, just empty table
        model.addAttribute("returnView", returnView);
        
        return "admin/tariff-price-history";
    }

    @GetMapping("/admin/tariffs/{id}/history/pdf")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> downloadTariffPdf(
            @PathVariable Long id,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.Tariff tariff = tariffService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));

            java.time.LocalDate targetDate = date != null ? date : java.time.LocalDate.now();
            java.util.List<com.proconsi.electrobazar.dto.TariffPriceEntryDTO> history = tariffPriceHistoryService
                    .getPricesForTariffAtDate(id, targetDate);

            byte[] pdfData = pdfReportService.generateTariffSheet(tariff, history);
            String filename = String.format("Tarifa_%s_%s.pdf", tariff.getName(), targetDate);

            org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(pdfData);
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error generating tariff PDF for ID " + id, e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
}
