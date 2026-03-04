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
    private final com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator recargoCalculator;

    @GetMapping("/productos-categorias")
    public String productsCategories(Model model, HttpSession session) {
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
        return "admin/productos-categorias";
    }

    @GetMapping("/admin")
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
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("roles", roleService.findAll());

        return "admin/admin";
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
        workerService.deleteById(id);
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

    @DeleteMapping("/admin/products/{id}/hard")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> hardDeleteProduct(@PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        try {
            productService.hardDeleteProduct(id);
            return org.springframework.http.ResponseEntity.ok().build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return org.springframework.http.ResponseEntity.status(409)
                    .body("No se puede eliminar: el producto tiene ventas asociadas");
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.status(500).body("Error al eliminar el producto");
        }
    }

    @GetMapping("/admin/download/invoice/{id}")
    @Transactional(readOnly = true)
    public org.springframework.http.ResponseEntity<?> downloadInvoicePdf(
            @org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.Sale sale = saleService.findById(id);
            com.proconsi.electrobazar.model.Invoice invoice = invoiceService.findBySaleId(id).orElse(null);

            byte[] pdfData = null;
            String filename = null;

            if (invoice != null) {
                // Recalculate tax breakdowns for invoice regeneration
                java.util.List<com.proconsi.electrobazar.dto.TaxBreakdown> taxBreakdowns = new java.util.ArrayList<>();
                boolean applyRecargo = sale.getCustomer() != null
                        && Boolean.TRUE.equals(sale.getCustomer().getHasRecargoEquivalencia());

                for (com.proconsi.electrobazar.model.SaleLine line : sale.getLines()) {
                    // Logic: Back-calculate Gross if needed, but here we assume line.unitPrice
                    // already follows the convention for that sale's customer.
                    // However, to be safe and use the Gross convention:
                    // We need the original Gross price. In this system, Product.price is Gross.
                    // But if it was an RE customer, line.unitPrice already includes RE.
                    // So we must be careful. Let's use the line's data.
                    taxBreakdowns.add(recargoCalculator.calculateLineBreakdown(
                            line.getProduct().getId(),
                            line.getProduct().getName(),
                            line.getUnitPrice(), // This is Gross (VAT inc)
                            line.getQuantity(),
                            line.getVatRate(),
                            applyRecargo));
                }

                java.math.BigDecimal totalBase = taxBreakdowns.stream()
                        .map(com.proconsi.electrobazar.dto.TaxBreakdown::getBaseAmount)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal totalVat = taxBreakdowns.stream()
                        .map(com.proconsi.electrobazar.dto.TaxBreakdown::getVatAmount)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal totalRecargo = taxBreakdowns.stream()
                        .map(com.proconsi.electrobazar.dto.TaxBreakdown::getRecargoAmount)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                // Regenerate Invoice PDF with breakdown
                pdfData = pdfReportService.generateInvoiceReport(sale, invoice, taxBreakdowns, applyRecargo, totalBase,
                        totalVat, totalRecargo);
                filename = "Factura_" + invoice.getInvoiceNumber() + ".pdf";
            } else {
                // Try to find the correlative Ticket
                java.util.Optional<com.proconsi.electrobazar.model.Ticket> ticketOpt = ticketService.findBySaleId(id);
                if (ticketOpt.isPresent()) {
                    com.proconsi.electrobazar.model.Ticket ticket = ticketOpt.get();

                    // Recalculate tax breakdowns for ticket regeneration
                    java.util.List<com.proconsi.electrobazar.dto.TaxBreakdown> taxBreakdowns = new java.util.ArrayList<>();
                    for (com.proconsi.electrobazar.model.SaleLine line : sale.getLines()) {
                        taxBreakdowns.add(recargoCalculator.calculateLineBreakdown(
                                line.getProduct().getId(),
                                line.getProduct().getName(),
                                line.getUnitPrice(),
                                line.getQuantity(),
                                line.getVatRate(),
                                ticket.isApplyRecargo()));
                    }

                    java.math.BigDecimal totalBase = taxBreakdowns.stream()
                            .map(com.proconsi.electrobazar.dto.TaxBreakdown::getBaseAmount)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    java.math.BigDecimal totalVat = taxBreakdowns.stream()
                            .map(com.proconsi.electrobazar.dto.TaxBreakdown::getVatAmount)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    java.math.BigDecimal totalRecargo = taxBreakdowns.stream()
                            .map(com.proconsi.electrobazar.dto.TaxBreakdown::getRecargoAmount)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                    pdfData = pdfReportService.generateTicketReport(
                            sale, taxBreakdowns, ticket.isApplyRecargo(),
                            totalBase, totalVat, totalRecargo);
                    filename = "Ticket_" + ticket.getTicketNumber() + ".pdf";
                }
            }

            if (pdfData == null) {
                log.warn("Document not found/could not be generated for sale {}", id);
                return org.springframework.http.ResponseEntity.status(404)
                        .body("No se encontró factura ni ticket correlativo para la venta#" + id);
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(pdfData);
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error generating document for sale " + id, e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
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
}
