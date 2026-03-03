package com.proconsi.electrobazar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for scheduling a new price for a product.
 * Used as the request body for POST /api/product-prices/{productId}/schedule.
 */
@Data
public class ProductPriceRequest {

    /**
     * The new price to apply. Must be positive.
     * Example: 29.99
     */
    private BigDecimal price;

    /**
     * The VAT rate as a decimal fraction (e.g., 0.21 for 21%, 0.10 for 10%).
     * Supported values: 0.21, 0.10, 0.04, 0.02
     * Defaults to 0.21 if not provided.
     */
    private BigDecimal vatRate;

    /**
     * The date and time from which this price becomes effective.
     * Format: "yyyy-MM-dd'T'HH:mm:ss"
     * Example: "2026-01-01T00:00:00" for a New Year price change.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startDate;

    /**
     * Optional label for this price entry.
     * Example: "Tarifa 2026", "Oferta Verano"
     */
    private String label;
}
