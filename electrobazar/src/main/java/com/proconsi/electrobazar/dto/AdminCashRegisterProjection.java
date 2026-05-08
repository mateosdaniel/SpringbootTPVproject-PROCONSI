package com.proconsi.electrobazar.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public interface AdminCashRegisterProjection {
    Long getId();
    LocalDateTime getOpeningTime();
    LocalDateTime getClosedAt();
    BigDecimal getOpeningBalance();
    BigDecimal getTotalSales();
    BigDecimal getClosingBalance();
    BigDecimal getDifference();
    String getWorkerUsername();
}
