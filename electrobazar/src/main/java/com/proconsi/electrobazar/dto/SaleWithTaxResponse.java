package com.proconsi.electrobazar.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.proconsi.electrobazar.model.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing the result of a sale processed with full tax calculation.
 * Includes the persisted sale ID, a complete tax breakdown per line,
 * and the aggregated totals.
 */
@Data
@Builder
public class SaleWithTaxResponse {

    /** The ID of the persisted Sale entity. */
    private Long saleId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /** The customer ID, if a customer was associated. */
    private Long customerId;

    /** The customer name, if a customer was associated. */
    private String customerName;

    /**
     * Whether Recargo de Equivalencia was applied to this sale.
     * True only if the customer has hasRecargoEquivalencia=true.
     */
    private boolean recargoEquivalenciaApplied;

    /** The payment method used. */
    private PaymentMethod paymentMethod;

    /** Amount received from the customer (for CASH payments). */
    private BigDecimal receivedAmount;

    /** Change returned to the customer (for CASH payments). */
    private BigDecimal changeAmount;

    /** Detailed tax breakdown per line item. */
    private List<TaxBreakdown> lines;

    // ── Aggregated totals ──────────────────────────────────────────────────────

    /** Sum of all base amounts (before taxes). */
    private BigDecimal totalBase;

    /** Sum of all VAT amounts. */
    private BigDecimal totalVat;

    /**
     * Sum of all Recargo de Equivalencia amounts.
     * Zero if RE was not applied.
     */
    private BigDecimal totalRecargo;

    /** Grand total = totalBase + totalVat + totalRecargo. */
    private BigDecimal grandTotal;

    /** Optional notes. */
    private String notes;
}
