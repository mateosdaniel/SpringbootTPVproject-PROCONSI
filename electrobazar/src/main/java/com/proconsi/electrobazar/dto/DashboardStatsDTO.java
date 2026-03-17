package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for the main administration dashboard statistics.
 * Provides a snapshot of the current shift and business performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    /** Whether there is a currently open cash register shift. */
    private boolean shiftActive;
    
    /** Time when the current shift was opened. */
    private LocalDateTime shiftOpeningTime;
    
    /** Total revenue for the current timeframe. */
    private BigDecimal revenue;
    
    /** Total number of sales performed. */
    private int salesCount;
    
    /** Name of the most sold product. */
    private String topProduct;
    
    /** Number of products currently with low stock. */
    private int lowStockCount;
    
    /** Opening balance of the current active register. */
    private BigDecimal openingBalance;
}
