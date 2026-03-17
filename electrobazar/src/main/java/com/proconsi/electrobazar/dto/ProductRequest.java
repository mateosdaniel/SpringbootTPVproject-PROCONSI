package com.proconsi.electrobazar.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO for creating or updating a product.
 * Contains the basic information required to persist a Product entity.
 */
@Data
public class ProductRequest {

    /** The name of the product. */
    @NotBlank
    @Size(max = 150)
    private String name;

    /** A brief description of the product. */
    @Size(max = 255)
    private String description;

    /** The retail price including taxes. */
    @NotNull
    @Positive
    private BigDecimal price;

    /** The base price before taxes (Net). */
    @Positive
    private BigDecimal basePriceNet;

    /** The ID of the tax rate to apply (e.g., 21% VAT). */
    @NotNull
    private Long taxRateId;

    /** Current stock level. */
    private Integer stock;

    /** The ID of the category this product belongs to. */
    private Long categoryId;

    /** URL to the product image. */
    private String imageUrl;

    /** Whether the product is currently active and available for sale. */
    private Boolean active;
}
