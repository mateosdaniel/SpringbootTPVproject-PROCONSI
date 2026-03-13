package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private boolean shiftActive;
    private LocalDateTime shiftOpeningTime;
    private BigDecimal revenue;
    private int salesCount;
    private String topProduct;
    private int lowStockCount;
    private BigDecimal openingBalance;
}
