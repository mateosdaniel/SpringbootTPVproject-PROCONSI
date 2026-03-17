package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.dto.ProductPriceResponse;
import com.proconsi.electrobazar.model.ProductPrice;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for managing temporal product prices (Price History Pattern).
 * Handles the full lifecycle of product pricing, including scheduling future changes.
 */
public interface ProductPriceService {

    /**
     * Returns the currently active price for a product at the given timestamp.
     * Results are cached to optimize performance.
     *
     * @param productId The ID of the product.
     * @param at        The timestamp to evaluate.
     * @return The active ProductPrice, or null if none is configured.
     */
    ProductPrice getCurrentPrice(Long productId, LocalDateTime at);

    /**
     * Schedules a new price for a product starting at a specific date.
     * Automatically closes the previous open-ended price.
     *
     * @param productId The ID of the product.
     * @param request   The pricing details (price, VAT, start date).
     * @return A response DTO containing the scheduled price info.
     */
    ProductPriceResponse schedulePrice(Long productId, ProductPriceRequest request);

    /**
     * Returns the complete chronologically ordered price history for a product.
     *
     * @param productId The ID of the product.
     * @return A list of price records.
     */
    List<ProductPriceResponse> getPriceHistory(Long productId);

    /**
     * Returns all prices that are scheduled to become active in the future.
     *
     * @return A list of future price schedules.
     */
    List<ProductPriceResponse> getFuturePrices();

    /**
     * Converts a ProductPrice entity to its Response DTO format.
     *
     * @param price    The entity to convert.
     * @param isActive Whether this price is currently the active one.
     * @return The populated DTO.
     */
    ProductPriceResponse toResponse(ProductPrice price, boolean isActive);

    /**
     * Executes bulk price updates for multiple products simultaneously.
     * Useful for global price increases (e.g., inflation adjustments).
     *
     * @param request The bulk update parameters.
     * @return A list of the newly created price records.
     */
    List<ProductPriceResponse> bulkSchedulePrice(BulkPriceUpdateRequest request);
}
