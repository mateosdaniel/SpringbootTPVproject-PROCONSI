package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

import com.proconsi.electrobazar.model.MeasurementUnit;

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
    private Long categoryId;
    private MeasurementUnit measurementUnit;
    private Integer priceDecimals;
    private BigDecimal vatRate;
    private String imageUrl;
    private boolean active;
}
