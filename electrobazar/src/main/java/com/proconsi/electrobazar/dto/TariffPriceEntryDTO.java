package com.proconsi.electrobazar.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TariffPriceEntryDTO {
    private Long productId;
    private String productName;
    private String categoryName;
    private BigDecimal basePrice;
    private BigDecimal netPrice;
    private BigDecimal vatRate;
    private BigDecimal priceWithVat;
    private BigDecimal reRate;
    private BigDecimal priceWithRe;
    private BigDecimal discountPercent;
    private LocalDate validFrom;
    private LocalDate validTo;
    private BigDecimal vatAmount;
    private BigDecimal reAmount;
    private boolean isFromHistory;
}
