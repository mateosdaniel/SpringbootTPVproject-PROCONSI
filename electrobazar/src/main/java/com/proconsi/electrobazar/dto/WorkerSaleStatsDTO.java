package com.proconsi.electrobazar.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO to represent sales statistics aggregated per worker.
 */
@Data
@Builder
@NoArgsConstructor
public class WorkerSaleStatsDTO {
    private Long workerId;
    private String workerName;
    private Long salesCount;
    private BigDecimal totalAmount;
    private BigDecimal cashAmount;
    private BigDecimal cardAmount;

    public WorkerSaleStatsDTO(Long workerId, String workerName, Long salesCount, Object totalAmount, Object cashAmount, Object cardAmount) {
        this.workerId = workerId;
        this.workerName = workerName;
        this.salesCount = salesCount;
        this.totalAmount = convertToBigDecimal(totalAmount);
        this.cashAmount = convertToBigDecimal(cashAmount);
        this.cardAmount = convertToBigDecimal(cardAmount);
    }

    private BigDecimal convertToBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return new BigDecimal(obj.toString());
        try {
            return new BigDecimal(obj.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
