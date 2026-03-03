package com.proconsi.electrobazar.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductRequest {
    
    @NotBlank @Size(max = 150)
    private String name;
    
    @Size(max = 255)
    private String description;
    
    @NotNull @Positive
    private BigDecimal price;
    
    @NotNull @DecimalMin("0.00") @DecimalMax("1.00")
    private BigDecimal ivaRate;
    
    private Integer stock;
    
    private Long categoryId;
    
    private String imageUrl;
}
