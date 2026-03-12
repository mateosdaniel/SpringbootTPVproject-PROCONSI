package com.proconsi.electrobazar.dto;

import com.proconsi.electrobazar.model.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for processing a sale with full tax calculation (VAT + optional Recargo de Equivalencia).
 * Used as the request body for POST /api/sales/with-tax.
 */
@Data
public class SaleWithTaxRequest {

    /**
     * Optional customer ID. If provided and the customer has hasRecargoEquivalencia=true,
     * the RE surcharge will be applied to all applicable line items.
     */
    private Long customerId;

    /** The payment method used for this sale. */
    private PaymentMethod paymentMethod;

    /** Optional notes for the sale. */
    private String notes;

    /**
     * Amount received from the customer (required for CASH payments).
     * Used to calculate change.
     */
    private BigDecimal receivedAmount;

    /** The worker ID processing this sale. */
    private Long workerId;

    /** The list of items being sold. */
    private List<SaleLineRequest> lines;


    /**
     * Represents a single line item in the sale request.
     */
    @Data
    public static class SaleLineRequest {

        /** The product ID. */
        private Long productId;

        /** The quantity to sell. */
        private Integer quantity;

        /**
         * Optional: override the price for this line.
         * If null, the current active price from the temporal pricing system will be used.
         */
        private BigDecimal overridePrice;
    }
}
