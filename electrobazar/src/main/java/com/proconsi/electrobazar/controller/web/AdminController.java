package com.proconsi.electrobazar.controller.web;

import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import com.proconsi.electrobazar.service.*;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final com.proconsi.electrobazar.service.CsvImportService csvImportService;

    private final com.proconsi.electrobazar.service.SaleService saleService;
    private final com.proconsi.electrobazar.service.CashRegisterService cashRegisterService;
    private final com.proconsi.electrobazar.service.PdfReportService pdfReportService;
    private final com.proconsi.electrobazar.service.WorkerService workerService;

    @GetMapping
    public String index(Model model, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return "redirect:/tpv";
        }

        // Cargar todos los datos requeridos por las distintas vistas del Admin
        // Dashboard
        model.addAttribute("products", productService.findAllWithCategory());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("sales",
                saleService.findBetween(java.time.LocalDateTime.now().minusYears(1), java.time.LocalDateTime.now()));
        model.addAttribute("cashRegisters", cashRegisterService.findAllClosed());
        model.addAttribute("workers", workerService.findAll());

        return "admin/index";
    }

    @PostMapping("/workers/save")
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

    @DeleteMapping("/workers/delete/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteWorker(@PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        workerService.deleteById(id);
        return org.springframework.http.ResponseEntity.ok().build();
    }

    @org.springframework.web.bind.annotation.PostMapping("/upload-csv")
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

    @GetMapping("/download/invoice/{id}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadInvoicePdf(
            @org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.Sale sale = saleService.findById(id);
            if (sale == null)
                return org.springframework.http.ResponseEntity.notFound().build();

            String dateStr = sale.getCreatedAt() != null
                    ? sale.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    : "UnknownDate";
            String filename = String.format("Factura_%s_ID%d.pdf", dateStr, id);
            java.io.File file = new java.io.File("facturas", filename);

            if (!file.exists()) {
                // Generar factura al vuelo si no existe
                file = pdfReportService.generateInvoiceReport(sale);
            }

            if (file == null || !file.exists())
                return org.springframework.http.ResponseEntity.notFound().build();

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(file.toURI());
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download/cash-close/{id}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadCashClosePdf(
            @org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.CashRegister register = cashRegisterService.findById(id);
            if (register == null)
                return org.springframework.http.ResponseEntity.notFound().build();

            String dateStr = register.getClosedAt() != null
                    ? register.getClosedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    : "UnknownDate";
            String filename = String.format("Cierre_Caja_%s_ID%d.pdf", dateStr, id);
            java.io.File file = new java.io.File("cierres_de_caja", filename);

            if (!file.exists()) {
                // Generar cierre al vuelo si no existe
                file = pdfReportService.generateCashCloseReport(register);
            }

            if (file == null || !file.exists())
                return org.springframework.http.ResponseEntity.notFound().build();

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(file.toURI());
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
}
