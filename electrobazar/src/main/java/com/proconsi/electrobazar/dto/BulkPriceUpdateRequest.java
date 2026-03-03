package com.proconsi.electrobazar.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BulkPriceUpdateRequest {
    
    @NotEmpty
    private List<Long> productIds;
    
    // Use either percentage OR fixedAmount (not both)
    private BigDecimal percentage;    // e.g., 10 for +10%
    private BigDecimal fixedAmount;   // e.g., 5.00 for +€5
    
    @NotNull @Future
    private LocalDateTime effectiveDate;
    
    private String label;
    
    private BigDecimal vatRate;       // Optional: change VAT too
}
