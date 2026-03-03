package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for a single cart line submitted when suspending a sale,
 * matching the JS cart structure: { productId, quantity, unitPrice }.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuspendedSaleLineRequest {

    /** ID of the product in the cart. */
    private Long productId;

    /** Number of units. */
    private int quantity;

    /** Unit price shown in the cart at suspension time. */
    private BigDecimal unitPrice;
}
