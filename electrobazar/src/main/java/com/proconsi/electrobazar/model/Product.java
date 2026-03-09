package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * The net base price (before VAT).
     * Convention: This is the primary stored value.
     */
    @Column(name = "base_price_net", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal basePriceNet = java.math.BigDecimal.ZERO;

    /**
     * Returns the Gross Price (VAT included).
     * Calculated on the fly: basePriceNet * (1 + ivaRate)
     */
    public java.math.BigDecimal getPrice() {
        if (basePriceNet == null)
            return java.math.BigDecimal.ZERO;
        java.math.BigDecimal rate = ivaRate != null ? ivaRate : java.math.BigDecimal.ZERO;
        return basePriceNet.multiply(java.math.BigDecimal.ONE.add(rate))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Sets the Net price based on a Gross price input.
     * basePriceNet = grossPrice / (1 + ivaRate)
     */
    public void setPrice(java.math.BigDecimal grossPrice) {
        if (grossPrice == null) {
            this.basePriceNet = java.math.BigDecimal.ZERO;
            return;
        }
        java.math.BigDecimal rate = ivaRate != null ? ivaRate : java.math.BigDecimal.ZERO;
        this.basePriceNet = grossPrice.divide(java.math.BigDecimal.ONE.add(rate), 10, java.math.RoundingMode.HALF_UP)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Column(nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private Integer stock = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "iva_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal ivaRate = null;
}