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
 *
 * <p>Base path: {@code /api/product-prices}</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code GET  /api/product-prices/{productId}/current}
 *       — Get the currently active price for a product</li>
 *   <li>{@code POST /api/product-prices/{productId}/schedule}
 *       — Schedule a new future price for a product</li>
 *   <li>{@code GET  /api/product-prices/{productId}/history}
 *       — Get the full price history for a product</li>
 *   <li>{@code GET  /api/product-prices/future}
 *       — Get all future-scheduled prices across all products</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/product-prices")
@RequiredArgsConstructor
public class ProductPriceApiRestController {

    private final ProductPriceService productPriceService;

    /**
     * Returns the currently active price for a product at the current moment.
     *
     * <p>This endpoint leverages the {@code @Cacheable} mechanism in the service layer,
     * so repeated calls for the same product will be served from cache.</p>
     *
     * <p>Example: {@code GET /api/product-prices/42/current}</p>
     *
     * @param productId the ID of the product
     * @return 200 with the active {@link ProductPriceResponse}, or 404 if no price is configured
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
     * Schedules a new price for a product starting at the specified date.
     *
     * <p>Business logic (handled by the service):</p>
     * <ul>
     *   <li>If a current open-ended price exists, its endDate is automatically set to
     *       {@code startDate - 1 second}.</li>
     *   <li>The new price is created with the provided startDate and a null endDate.</li>
     *   <li>The product price cache is evicted after the operation.</li>
     * </ul>
     *
     * <p>Example request body for a New Year price increase:</p>
     * <pre>{@code
     * {
     *   "price": 29.99,
     *   "vatRate": 0.21,
     *   "startDate": "2026-01-01T00:00:00",
     *   "label": "Tarifa 2026"
     * }
     * }</pre>
     *
     * @param productId the ID of the product
     * @param request   the price scheduling request
     * @return 201 with the newly created {@link ProductPriceResponse}
     */
    @PostMapping("/{productId}/schedule")
    public ResponseEntity<ProductPriceResponse> schedulePrice(
            @PathVariable Long productId,
            @RequestBody ProductPriceRequest request) {
        ProductPriceResponse response = productPriceService.schedulePrice(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the complete price history for a product, ordered by startDate descending.
     *
     * <p>Example: {@code GET /api/product-prices/42/history}</p>
     *
     * @param productId the ID of the product
     * @return 200 with the list of all {@link ProductPriceResponse} records
     */
    @GetMapping("/{productId}/history")
    public ResponseEntity<List<ProductPriceResponse>> getPriceHistory(@PathVariable Long productId) {
        return ResponseEntity.ok(productPriceService.getPriceHistory(productId));
    }

    /**
     * Returns all future-scheduled prices across all products.
     *
     * <p>Useful for administrative dashboards to preview upcoming price changes.</p>
     *
     * <p>Example: {@code GET /api/product-prices/future}</p>
     *
     * @return 200 with the list of future {@link ProductPriceResponse} records
     */
    @GetMapping("/future")
    public ResponseEntity<List<ProductPriceResponse>> getFuturePrices() {
        return ResponseEntity.ok(productPriceService.getFuturePrices());
    }

    /**
     * Returns the VAT-to-RE rate mapping for informational purposes.
     * Useful for front-end display of applicable tax rates.
     *
     * <p>Example: {@code GET /api/product-prices/re-rates}</p>
     *
     * @return 200 with a map of VAT rates to RE rates
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
     * Bulk schedules price updates for multiple products.
     *
     * <p>Allows selecting multiple products and scheduling price changes (either by percentage
     * or fixed amount increase) with a future effective date.</p>
     *
     * <p>Example request body:</p>
     * <pre>{@code
     * {
     *   "productIds": [1, 5, 12, 20],
     *   "percentage": 10,
     *   "effectiveDate": "2026-04-01T00:00:00",
     *   "label": "春季涨价"
     * }
     * }</pre>
     *
     * <p>Or with fixed amount:</p>
     * <pre>{@code
     * {
     *   "productIds": [1, 5, 12],
     *   "fixedAmount": 5.00,
     *   "effectiveDate": "2026-04-01T00:00:00",
     *   "label": "April increase"
     * }
     * }</pre>
     *
     * @param request the bulk price update request
     * @return 201 with the list of newly created {@link ProductPriceResponse}
     */
    @PostMapping("/bulk-schedule")
    public ResponseEntity<List<ProductPriceResponse>> bulkSchedulePrice(
            @RequestBody BulkPriceUpdateRequest request) {
        List<ProductPriceResponse> responses = productPriceService.bulkSchedulePrice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }
}
