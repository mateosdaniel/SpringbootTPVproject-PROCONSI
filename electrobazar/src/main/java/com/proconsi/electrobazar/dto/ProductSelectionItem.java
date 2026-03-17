package com.proconsi.electrobazar.dto;

import java.math.BigDecimal;

/**
 * Lightweight DTO representing a product for selection in the TPV interface.
 * 
 * @param id The product ID.
 * @param name The product name.
 * @param currentPrice The current active price.
 * @param currentVat The current VAT percentage.
 * @param categoryId The ID of the assigned category.
 * @param categoryName The name of the assigned category.
 */
public record ProductSelectionItem(
    Long id, 
    String name, 
    BigDecimal currentPrice, 
    BigDecimal currentVat,
    Long categoryId,
    String categoryName
) {}
