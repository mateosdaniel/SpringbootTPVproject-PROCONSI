package com.proconsi.electrobazar.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleSummaryResponse {
    private long totalSalesCount;
    private BigDecimal totalSalesAmount;
    private BigDecimal totalCashAmount;
    private BigDecimal totalCardAmount;
    private long totalCancelledCount;
    private BigDecimal totalCancelledAmount;

    /**
     * Helper constructor to handle JPQL result type variations (Long vs Integer vs
     * Object).
     * Used by "new com.proconsi.electrobazar.dto.SaleSummaryResponse(...)" queries.
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

    private long toLong(Object val) {
        if (val == null)
            return 0L;
        if (val instanceof Number)
            return ((Number) val).longValue();
        return 0L;
    }

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
