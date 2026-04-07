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
public class AdminCashClosingListingDTO {
    private Long id;
    private LocalDateTime openingTime;
    private LocalDateTime closedAt;
    private BigDecimal openingBalance;
    private BigDecimal totalSales;
    private BigDecimal totalCalculated;
    private BigDecimal closingBalance;
    private BigDecimal difference;
    private String workerUsername;
}
