package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.dto.ProductPriceResponse;
import com.proconsi.electrobazar.model.ProductPrice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service interface for managing temporal product prices (Price History Pattern).
 *
 * <p>This service handles the full lifecycle of product pricing:</p>
 * <ul>
 *   <li>Retrieving the currently active price for a product at any point in time</li>
 *   <li>Scheduling future price changes with automatic end-date management</li>
 *   <li>Querying price history for a product</li>
 * </ul>
 */
public interface ProductPriceService {

    /**
     * Returns the currently active price for a product at the given timestamp.
     *
     * <p>This method is cached via {@code @Cacheable} to avoid excessive database hits.
     * The cache key is composed of {@code productId} and the truncated minute of {@code at}
     * to balance freshness and performance.</p>
     *
     * @param productId the ID of the product
     * @param at        the timestamp to evaluate (typically {@code LocalDateTime.now()})
     * @return the active {@link ProductPrice}, or {@code null} if no price is configured
     */
    ProductPrice getCurrentPrice(Long productId, LocalDateTime at);

    /**
     * Schedules a new price for a product starting at the given date.
     *
     * <p>Business rules enforced by this method:</p>
     * <ol>
     *   <li>If there is a currently open-ended price (endDate IS NULL), its endDate is
     *       automatically set to one second before the new price's startDate.</li>
     *   <li>The new price is persisted with the provided startDate and a null endDate
     *       (open-ended), unless a subsequent price is already scheduled after it.</li>
     *   <li>The cache for the affected product is evicted after the operation.</li>
     * </ol>
     *
     * <p>Example: If the current price is €10.00 (open-ended) and a new price of €12.00
     * is scheduled for Jan 1st 2026 00:00:00, the current price's endDate will be set to
     * Dec 31st 2025 23:59:59, and the new price will start on Jan 1st 2026.</p>
     *
     * @param productId the ID of the product
     * @param request   the price scheduling request containing price, vatRate, startDate, and label
     * @return the newly created {@link ProductPriceResponse}
     * @throws com.proconsi.electrobazar.exception.ResourceNotFoundException if the product is not found
     * @throws IllegalArgumentException if the startDate is null or conflicts with existing prices
     */
    ProductPriceResponse schedulePrice(Long productId, ProductPriceRequest request);

    /**
     * Returns the complete price history for a product, ordered by startDate descending.
     *
     * @param productId the ID of the product
     * @return list of all {@link ProductPriceResponse} records for the product
     */
    List<ProductPriceResponse> getPriceHistory(Long productId);

    /**
     * Returns all future-scheduled prices across all products.
     * Useful for administrative dashboards and the daily scheduler.
     *
     * @return list of {@link ProductPriceResponse} records with startDate in the future
     */
    List<ProductPriceResponse> getFuturePrices();

    /**
     * Converts a {@link ProductPrice} entity to a {@link ProductPriceResponse} DTO.
     *
     * @param price     the entity to convert
     * @param isActive  whether this price is currently active
     * @return the populated DTO
     */
    ProductPriceResponse toResponse(ProductPrice price, boolean isActive);

    /**
     * Bulk schedules price updates for multiple products.
     *
     * <p>Business rules:</p>
     * <ol>
     *   <li>For each productId in the request, find the current open-ended price</li>
     *   <li>Calculate the new price (percentage or fixedAmount increase)</li>
     *   <li>Close current open price (endDate = effectiveDate - 1 second)</li>
     *   <li>Create new ProductPrice with new price and effectiveDate</li>
     *   <li>Use single cache eviction at the end (not per product)</li>
     * </ol>
     *
     * @param request the bulk price update request
     * @return list of newly created {@link ProductPriceResponse}
     */
    List<ProductPriceResponse> bulkSchedulePrice(BulkPriceUpdateRequest request);
}
