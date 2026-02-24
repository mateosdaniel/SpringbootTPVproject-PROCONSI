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

    @GetMapping("/{id}")
    public ResponseEntity<Sale> getById(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.findById(id));
    }

    @GetMapping("/today")
    public ResponseEntity<List<Sale>> getToday() {
        return ResponseEntity.ok(saleService.findToday());
    }

    // El body que espera:
    // {
    //   "paymentMethod": "CASH",
    //   "notes": "opcional",
    //   "lines": [
    //     { "product": { "id": 1 }, "quantity": 2 },
    //     { "product": { "id": 3 }, "quantity": 1 }
    //   ]
    // }
    @PostMapping
    public ResponseEntity<Sale> create(@RequestBody Sale sale) {
        List<SaleLine> lines = sale.getLines().stream().map(line -> {
            Product product = productService.findById(line.getProduct().getId());
            return SaleLine.builder()
                    .product(product)
                    .quantity(line.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
        }).collect(Collectors.toList());

        Sale saved = saleService.createSale(lines, sale.getPaymentMethod(), sale.getNotes());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}