package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.service.CustomerService;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * REST Controller for managing standard sales.
 * Handles sale retrieval, filtering by date range, and basic sale creation.
 */
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleApiRestController {

    private final SaleService saleService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final WorkerService workerService;

    /**
     * Retrieves all recorded sales.
     * @return List of all {@link Sale} entities.
     */
    @GetMapping
    public ResponseEntity<List<Sale>> getAll() {
        return ResponseEntity.ok(saleService.findAll());
    }

    /**
     * Retrieves a single sale by its ID.
     * @param id Internal sale ID.
     * @return The requested {@link Sale}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Sale> getById(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.findById(id));
    }

    /**
     * Retrieves all sales performed during the current day.
     * @return List of today's sales.
     */
    @GetMapping("/today")
    public ResponseEntity<List<Sale>> getToday() {
        return ResponseEntity.ok(saleService.findToday());
    }

    /**
     * Retrieves a statistical summary of today's sales (totals, counts, etc.).
     * @return {@link SaleSummaryResponse} data.
     */
    @GetMapping("/stats/today")
    public ResponseEntity<SaleSummaryResponse> getTodayStats() {
        return ResponseEntity.ok(saleService.getSummaryToday());
    }

    /**
     * Filters sales within a specific date and time range.
     * @param from Start date-time (ISO format).
     * @param to End date-time (ISO format).
     * @return List of sales in the specified range.
     */
    @GetMapping("/range")
    public ResponseEntity<List<Sale>> getRange(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(saleService.findBetween(from, to));
    }

    /**
     * Creates a new sale.
     * Note: For advanced tax and temporal pricing, use the '/api/sales/with-tax' endpoint instead.
     * 
     * @param sale The sale data (lines, customer info, payment method).
     * @param workerId ID of the worker performing the sale (from header).
     * @return The saved {@link Sale} entity.
     */
    @PostMapping
    public ResponseEntity<Sale> create(
            @RequestBody Sale sale,
            @RequestHeader(value = "X-Worker-Id", required = false) Long workerId) {

        Worker worker = null;
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

        Customer validCustomer = null;
        if (sale.getCustomer() != null && sale.getCustomer().getName() != null
                && !sale.getCustomer().getName().isBlank()) {
            Customer newCust = Customer.builder()
                    .name(sale.getCustomer().getName())
                    .type(sale.getCustomer().getType() != null ? sale.getCustomer().getType()
                            : Customer.CustomerType.INDIVIDUAL)
                    .build();
            validCustomer = customerService.save(newCust);
        }

        Sale saved;
        if (validCustomer != null) {
            saved = saleService.createSale(lines, sale.getPaymentMethod(), sale.getNotes(),
                    sale.getReceivedAmount(), validCustomer, worker);
        } else {
            saved = saleService.createSale(lines, sale.getPaymentMethod(), sale.getNotes(),
                    sale.getReceivedAmount(), worker);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Cancels an existing sale.
     * @param id The sale ID to cancel.
     * @param body Request body containing the reason for cancellation.
     * @param workerId ID of the worker authorizing the cancellation.
     * @return 200 OK.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Worker-Id", required = false) Long workerId) {

        Worker worker = null;
        if (workerId != null) {
            worker = workerService.findById(workerId).orElse(null);
        }
        String reason = body.getOrDefault("reason", "Anulación desde API");
        saleService.cancelSale(id, worker, reason);
        return ResponseEntity.ok().build();
    }
}
