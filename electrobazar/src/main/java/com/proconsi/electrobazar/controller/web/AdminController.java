package com.proconsi.electrobazar.controller.web;

import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import com.proconsi.electrobazar.service.*;

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

    private final com.proconsi.electrobazar.service.DocumentService documentService;

    @GetMapping("/admin/download/invoice/{id}")
    public org.springframework.http.ResponseEntity<?> downloadInvoicePdf(
            @org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.Sale sale = saleService.findById(id);
            if (sale == null)
                return org.springframework.http.ResponseEntity.status(404).body("Venta no encontrada.");

            com.proconsi.electrobazar.model.Invoice invoice = invoiceService.findBySaleId(id).orElse(null);

            byte[] pdfData = null;
            String filename = null;

            // 1. Intentar sacar de la tabla INVOICES si es una factura
            if (invoice != null && invoice.getPdfData() != null) {
                pdfData = invoice.getPdfData();
                filename = invoice.getPdfFilename();
            }

            // 2. Si no es factura o no tiene PDF en el registro de factura, buscar en
            // STORED_DOCUMENTS
            if (pdfData == null) {
                com.proconsi.electrobazar.model.DocumentType docType = invoice != null
                        ? com.proconsi.electrobazar.model.DocumentType.INVOICE
                        : com.proconsi.electrobazar.model.DocumentType.TICKET;
                Long refId = invoice != null ? invoice.getId() : id;

                java.util.Optional<com.proconsi.electrobazar.model.StoredDocument> storedDoc = documentService
                        .findByTypeAndReference(docType, refId);

                if (storedDoc.isPresent()) {
                    pdfData = storedDoc.get().getData();
                    filename = storedDoc.get().getFilename();
                }
            }

            if (pdfData == null) {
                return org.springframework.http.ResponseEntity.status(404)
                        .body("El documento PDF no existe en la base de datos.");
            }

            if (filename == null) {
                filename = (invoice != null ? "Factura_" + invoice.getInvoiceNumber() : "Ticket_" + id) + ".pdf";
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(pdfData);
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error downloading document for sale " + id, e);
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/download/cash-close/{id}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadCashClosePdf(
            @org.springframework.web.bind.annotation.PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }

        try {
            com.proconsi.electrobazar.model.CashRegister register = cashRegisterService.findById(id);
            if (register == null)
                return org.springframework.http.ResponseEntity.notFound().build();

            java.util.Optional<com.proconsi.electrobazar.model.StoredDocument> storedDoc = documentService
                    .findByTypeAndReference(com.proconsi.electrobazar.model.DocumentType.CASH_CLOSE, id);

            byte[] pdfData;
            String filename;

            if (storedDoc.isPresent()) {
                pdfData = storedDoc.get().getData();
                filename = storedDoc.get().getFilename();
            } else {
                // Regenerate and store if missing
                pdfData = pdfReportService.generateCashCloseReport(register);
                String dateStr = register.getClosedAt() != null
                        ? register.getClosedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        : "UnknownDate";
                filename = String.format("Cierre_Caja_%s_ID%d.pdf", dateStr, id);
                documentService.store(com.proconsi.electrobazar.model.DocumentType.CASH_CLOSE, id, filename, pdfData);
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(pdfData);
            return org.springframework.http.ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError().build();
        }
    }
}
