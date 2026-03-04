package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleApiRestController {

    private final SaleService saleService;
    private final ProductService productService;
    private final com.proconsi.electrobazar.service.CustomerService customerService;
    private final com.proconsi.electrobazar.service.PdfReportService pdfReportService;
    private final com.proconsi.electrobazar.service.WorkerService workerService;
    private final com.proconsi.electrobazar.service.InvoiceService invoiceService;
    private final com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator recargoCalculator;

    @GetMapping
    public ResponseEntity<List<Sale>> getAll() {
        return ResponseEntity.ok(saleService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sale> getById(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.findById(id));
    }

    @GetMapping("/today")
    public ResponseEntity<List<Sale>> getToday() {
        return ResponseEntity.ok(saleService.findToday());
    }

    @GetMapping("/stats/today")
    public ResponseEntity<com.proconsi.electrobazar.dto.SaleSummaryResponse> getTodayStats() {
        return ResponseEntity.ok(saleService.getSummaryToday());
    }

    @GetMapping("/range")
    public ResponseEntity<List<Sale>> getRange(
            @RequestParam("from") String from,
            @RequestParam("to") String to) {
        java.time.LocalDateTime start = java.time.LocalDateTime.parse(from);
        java.time.LocalDateTime end = java.time.LocalDateTime.parse(to);
        return ResponseEntity.ok(saleService.findBetween(start, end));
    }

    @GetMapping("/{id}/ticket")
    public ResponseEntity<org.springframework.core.io.Resource> getTicket(@PathVariable Long id) {
        Sale sale = saleService.findById(id);
        com.proconsi.electrobazar.model.Invoice invoice = invoiceService.findBySaleId(id).orElse(null);
        // Recalculate tax breakdowns for PDF generation
        java.util.List<com.proconsi.electrobazar.dto.TaxBreakdown> taxBreakdowns = new java.util.ArrayList<>();
        boolean applyRecargo = sale.getCustomer() != null
                && Boolean.TRUE.equals(sale.getCustomer().getHasRecargoEquivalencia());

        for (com.proconsi.electrobazar.model.SaleLine line : sale.getLines()) {
            java.math.BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate()
                    : new java.math.BigDecimal("0.21");
            taxBreakdowns.add(recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(),
                    line.getProduct().getName(),
                    line.getUnitPrice(),
                    line.getQuantity(),
                    vatRate,
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

        byte[] pdfData = pdfReportService.generateInvoiceReport(sale, invoice, taxBreakdowns, applyRecargo, totalBase,
                totalVat, totalRecargo);

        String invoiceLabel = invoice != null ? invoice.getInvoiceNumber() : ("Ticket_" + id);
        String filename = "Ticket_" + invoiceLabel + ".pdf";

        org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(pdfData);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @PostMapping
    public ResponseEntity<Sale> create(
            @RequestBody Sale sale,
            @RequestHeader(value = "X-Worker-Id", required = false) Long workerId) {

        com.proconsi.electrobazar.model.Worker worker = null;
        if (workerId != null) {
            worker = workerService.findById(workerId).orElse(null);
        }

        List<SaleLine> lines = sale.getLines().stream().map(line -> {
            Product product = productService.findById(line.getProduct().getId());
            return SaleLine.builder()
                    .product(product)
                    .quantity(line.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
        }).collect(Collectors.toList());

        com.proconsi.electrobazar.model.Customer validCustomer = null;
        if (sale.getCustomer() != null && sale.getCustomer().getName() != null
                && !sale.getCustomer().getName().isBlank()) {
            com.proconsi.electrobazar.model.Customer newCust = com.proconsi.electrobazar.model.Customer.builder()
                    .name(sale.getCustomer().getName())
                    .type(sale.getCustomer().getType() != null ? sale.getCustomer().getType()
                            : com.proconsi.electrobazar.model.Customer.CustomerType.INDIVIDUAL)
                    .build();
            validCustomer = customerService.save(newCust);
        }

        Sale saved;
        if (validCustomer != null) {
            saved = saleService.createSale(lines, sale.getPaymentMethod(), sale.getNotes(), sale.getReceivedAmount(),
                    validCustomer, worker);
        } else {
            saved = saleService.createSale(lines, sale.getPaymentMethod(), sale.getNotes(), sale.getReceivedAmount(),
                    worker);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
