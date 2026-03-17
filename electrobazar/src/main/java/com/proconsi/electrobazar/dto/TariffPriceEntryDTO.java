package com.proconsi.electrobazar.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO representing a product's price entry within a specific tariff at a given point in time.
 * Includes detailed breakdown of base price, taxes (VAT and RE), and final prices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TariffPriceEntryDTO {
    /** The product ID. */
    private Long productId;
    
    /** The name of the product. */
    private String productName;
    
    /** The category name of the product. */
    private String categoryName;
    
    /** The base retail price including VAT. */
    private BigDecimal basePrice;
    
    /** The base price before taxes (Net). */
    private BigDecimal netPrice;
    
    /** The VAT rate applied. */
    private BigDecimal vatRate;
    
    /** The calculated VAT amount per unit. */
    private BigDecimal vatAmount;
    
    /** The final price including VAT (after tariff discounts). */
    private BigDecimal priceWithVat;
    
    /** The Equivalency Surcharge rate, if applicable. */
    private BigDecimal reRate;
    
    /** The calculated Equivalency Surcharge amount per unit. */
    private BigDecimal reAmount;
    
    /** The final price including both VAT and Equivalency Surcharge. */
    private BigDecimal priceWithRe;
    
    /** The percentage discount applied by this tariff compared to the base price. */
    private BigDecimal discountPercent;
    
    /** The date from which this price entry is valid. */
    private LocalDate validFrom;
    
    /** The date until which this price entry is valid (null if current). */
    private LocalDate validTo;
    
    /** Flag indicating if this data was retrieved from historical records. */
    private boolean isFromHistory;
}
