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

    /** The payment method used for this sale (CASH or CARD). */
    private PaymentMethod paymentMethod;

    /** Optional notes or comments for the sale. */
    private String notes;

    /**
     * If true and a customer is provided, an invoice (factura) will be generated
     * instead of a standard ticket.
     */
    private Boolean requestInvoice;

    /**
     * Amount received from the customer. Required for CASH payments to calculate change.
     */
    private BigDecimal receivedAmount;

    /** The ID of the worker processing this sale. */
    private Long workerId;

    /** The list of items (product + quantity) being sold. */
    private List<SaleLineRequest> lines;

    /**
     * Represents a single line item in the sale request.
     */
    @Data
    public static class SaleLineRequest {

        /** The ID of the product. */
        private Long productId;

        /** The number of units to sell. */
        private Integer quantity;

        /**
         * Optional: override the default price for this specific line.
         * If null, the system will use the currently active price.
         */
        private BigDecimal overridePrice;
    }
}
