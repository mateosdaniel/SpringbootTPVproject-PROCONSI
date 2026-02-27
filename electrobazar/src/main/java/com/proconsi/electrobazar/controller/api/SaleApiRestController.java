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

    @GetMapping("/{id}")
    public ResponseEntity<Sale> getById(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.findById(id));
    }

    @GetMapping("/today")
    public ResponseEntity<List<Sale>> getToday() {
        return ResponseEntity.ok(saleService.findToday());
    }

    @GetMapping("/{id}/ticket")
    public ResponseEntity<org.springframework.core.io.Resource> getTicket(@PathVariable Long id) {
        Sale sale = saleService.findById(id);
        java.io.File pdfFile = pdfReportService.generateInvoiceReport(sale);
        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(pdfFile);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdfFile.getName() + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(resource);
    }

    // El body que espera:
    // {
    // "paymentMethod": "CASH",
    // "notes": "opcional",
    // "lines": [
    // { "product": { "id": 1 }, "quantity": 2 },
    // { "product": { "id": 3 }, "quantity": 1 }
    // ]
    // }
    @PostMapping
    public ResponseEntity<Sale> create(@RequestBody Sale sale, jakarta.servlet.http.HttpSession session) {
        com.proconsi.electrobazar.model.Worker worker = (com.proconsi.electrobazar.model.Worker) session
                .getAttribute("worker");

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