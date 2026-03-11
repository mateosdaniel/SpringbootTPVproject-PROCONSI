package com.proconsi.electrobazar.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 255)
    private String description;

    @NotNull
    @Positive
    private BigDecimal price;

    @Positive
    private BigDecimal basePriceNet;

    @NotNull
    private Long taxRateId;

    private Integer stock;

    private Long categoryId;

    private String imageUrl;

    private Boolean active;
}
