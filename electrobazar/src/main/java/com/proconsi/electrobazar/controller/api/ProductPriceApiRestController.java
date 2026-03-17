package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.dto.ProductPriceResponse;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.service.ProductPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing temporal product prices.
 * Handles the historical tracking and future scheduling of product prices and VAT rates.
 */
@RestController
@RequestMapping("/api/product-prices")
@RequiredArgsConstructor
public class ProductPriceApiRestController {

    private final ProductPriceService productPriceService;

    /**
     * Returns the currently active price for a product at the current moment.
     * This endpoint leverages caching in the service layer.
     *
     * @param productId The ID of the product.
     * @return 200 with the active price response, or 404 if not found.
     */
    @GetMapping("/{productId}/current")
    public ResponseEntity<ProductPriceResponse> getCurrentPrice(@PathVariable Long productId) {
        ProductPrice activePrice = productPriceService.getCurrentPrice(productId, LocalDateTime.now());
        if (activePrice == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(productPriceService.toResponse(activePrice, true));
    }

    /**
     * Schedules a new price for a product starting at a specific date.
     * Existing open-ended prices are automatically closed to accommodate the new schedule.
     *
     * @param productId The ID of the product.
     * @param request Price details including effective start date.
     * @return 201 Created with the new price schedule.
     */
    @PostMapping("/{productId}/schedule")
    public ResponseEntity<ProductPriceResponse> schedulePrice(
            @PathVariable Long productId,
            @RequestBody ProductPriceRequest request) {
        ProductPriceResponse response = productPriceService.schedulePrice(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the complete price history for a product.
     * @param productId The ID of the product.
     * @return List of all historical and future price records.
     */
    @GetMapping("/{productId}/history")
    public ResponseEntity<List<ProductPriceResponse>> getPriceHistory(@PathVariable Long productId) {
        return ResponseEntity.ok(productPriceService.getPriceHistory(productId));
    }

    /**
     * Retrieves all future-scheduled price changes across the entire catalog.
     * @return List of upcoming price records.
     */
    @GetMapping("/future")
    public ResponseEntity<List<ProductPriceResponse>> getFuturePrices() {
        return ResponseEntity.ok(productPriceService.getFuturePrices());
    }

    /**
     * Returns the informational mapping between standard VAT rates and RE surcharge rates.
     * @return Map of VAT labels to RE labels.
     */
    @GetMapping("/re-rates")
    public ResponseEntity<Map<String, String>> getReRates() {
        Map<String, String> rates = Map.of(
                "21% IVA", "5.2% RE",
                "10% IVA", "1.4% RE",
                "4% IVA",  "0.5% RE",
                "2% IVA",  "0.15% RE"
        );
        return ResponseEntity.ok(rates);
    }

    /**
     * Bulk schedules price updates for multiple products simultaneously.
     * Supports both percentage-based and fixed-amount adjustments.
     *
     * @param request Bulk update details.
     * @return 201 Created with the list of generated price schedules.
     */
    @PostMapping("/bulk-schedule")
    public ResponseEntity<List<ProductPriceResponse>> bulkSchedulePrice(
            @RequestBody BulkPriceUpdateRequest request) {
        List<ProductPriceResponse> responses = productPriceService.bulkSchedulePrice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }
}
