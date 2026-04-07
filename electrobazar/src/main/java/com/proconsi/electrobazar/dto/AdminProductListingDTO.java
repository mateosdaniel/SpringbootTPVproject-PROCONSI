package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProductListingDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal stock;
    private String categoryName;
    private String measurementUnitSymbol;
    private BigDecimal vatRate;
    private String imageUrl;
    private boolean active;
}
