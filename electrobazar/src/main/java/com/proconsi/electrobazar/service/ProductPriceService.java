package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.BulkPriceMatrixUpdateRequest;
import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.dto.ProductPriceResponse;
import com.proconsi.electrobazar.dto.PriceMatrixSummaryDTO;
import com.proconsi.electrobazar.model.ProductPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;


/**
 * Service interface for managing temporal product prices (Price History Pattern).
 * Handles the full lifecycle of product pricing, including scheduling future changes.
 */
public interface ProductPriceService {

    ProductPrice getCurrentPrice(Long productId, LocalDateTime at);

    /**
     * Retrieves future prices with optional search filtering (paginated).
     */
    Page<ProductPrice> getFilteredFuturePrices(String search, Pageable pageable);

    /**
     * Bulk fetch active prices for multiple products to avoid N+1 problems.
     * @param productIds List of product IDs.
     * @param at Timestamp to evaluate.
     * @return List of active ProductPrice entities.
     */
    List<ProductPrice> getActivePrices(List<Long> productIds, LocalDateTime at);

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

    /**
     * Executes bulk price matrix updates for multiple product + tariff combinations.
     *
     * @param request The bulk update matrix.
     */
    void bulkMatrixUpdate(BulkPriceMatrixUpdateRequest request);
    
    /**
     * Lists current scheduled price matrix changes.
     */
    List<PriceMatrixSummaryDTO> getPendingMatrixUpdates();
    
    /**
     * Lists recently applied price changes.
     */
    List<PriceMatrixSummaryDTO> getMatrixUpdateHistory();
    
    /**
     * Cancels a pending (future) price update.
     * @param id The ID of the scheduled ProductPrice record.
     */
    void deletePendingPrice(Long id);
}
