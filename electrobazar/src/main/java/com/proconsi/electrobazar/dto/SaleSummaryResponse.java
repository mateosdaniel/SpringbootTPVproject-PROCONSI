package com.proconsi.electrobazar.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO representing a summary of sales performance, typically for a specific period (e.g., daily).
 * Includes counts and totals categorized by payment method and status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleSummaryResponse {
    /** Total number of successful sales. */
    private long totalSalesCount;
    
    /** Total revenue from successful sales. */
    private BigDecimal totalSalesAmount;
    
    /** Total amount collected via cash payments. */
    private BigDecimal totalCashAmount;
    
    /** Total amount collected via card payments. */
    private BigDecimal totalCardAmount;
    
    /** Number of sales that were cancelled. */
    private long totalCancelledCount;
    
    /** Total amount from cancelled sales. */
    private BigDecimal totalCancelledAmount;

    /**
     * Custom constructor to handle JPQL result type variations (Long vs Integer vs Object).
     * This is useful when using "SELECT new com.proconsi.electrobazar.dto.SaleSummaryResponse(...)" queries
     * where different JPA providers or database drivers might yield different numeric wrappers.
     *
     * @param totalSalesCount The raw total sales count object.
     * @param totalSalesAmount The raw total sales amount object.
     * @param totalCashAmount The raw total cash amount object.
     * @param totalCardAmount The raw total card amount object.
     * @param totalCancelledCount The raw total cancelled count object.
     * @param totalCancelledAmount The raw total cancelled amount object.
     */
    public SaleSummaryResponse(Object totalSalesCount, Object totalSalesAmount, Object totalCashAmount,
            Object totalCardAmount, Object totalCancelledCount, Object totalCancelledAmount) {
        this.totalSalesCount = toLong(totalSalesCount);
        this.totalSalesAmount = toBigDecimal(totalSalesAmount);
        this.totalCashAmount = toBigDecimal(totalCashAmount);
        this.totalCardAmount = toBigDecimal(totalCardAmount);
        this.totalCancelledCount = toLong(totalCancelledCount);
        this.totalCancelledAmount = toBigDecimal(totalCancelledAmount);
    }

    /**
     * Safely converts an object to long.
     */
    private long toLong(Object val) {
        if (val == null)
            return 0L;
        if (val instanceof Number)
            return ((Number) val).longValue();
        return 0L;
    }

    /**
     * Safely converts an object to BigDecimal.
     */
    private BigDecimal toBigDecimal(Object val) {
        if (val == null)
            return BigDecimal.ZERO;
        if (val instanceof BigDecimal)
            return (BigDecimal) val;
        if (val instanceof Number)
            return BigDecimal.valueOf(((Number) val).doubleValue());
        return BigDecimal.ZERO;
    }
}
