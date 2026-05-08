package com.proconsi.electrobazar.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.proconsi.electrobazar.model.SaleReturn.ReturnType;

public interface AdminReturnProjection {
    Long getId();
    String getReturnNumber();
    LocalDateTime getCreatedAt();
    ReturnType getType();
    String getReason();
    String getWorkerUsername();
    String getPaymentMethod();
    BigDecimal getTotalRefunded();
    String getOriginalInvoiceNumber();
    String getOriginalTicketNumber();
    Long getOriginalSaleId();
}
