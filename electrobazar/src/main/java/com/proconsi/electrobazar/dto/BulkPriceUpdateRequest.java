package com.proconsi.electrobazar.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for bulk price update requests.
 * Allows updating prices for multiple products by a percentage or fixed amount.
 */
@Data
public class BulkPriceUpdateRequest {
    
    /** List of product IDs to update. */
    @NotEmpty
    private List<Long> productIds;
    
    /** Percentage to increase/decrease the price. E.g., 10 for +10%. */
    private BigDecimal percentage;
    
    /** Fixed amount to increase/decrease the price. E.g., 5.00 for +€5. */
    private BigDecimal fixedAmount;
    
    /** Date and time when the new prices become effective. */
    @NotNull @Future
    private LocalDateTime effectiveDate;
    
    /** Optional label for the batch update. */
    private String label;
    
    /** Optional: also update the VAT rate for these products. */
    private BigDecimal vatRate;
    
    /** Optional: specific tariffs to which this change applies. */
    private List<Long> tariffIds;
}
