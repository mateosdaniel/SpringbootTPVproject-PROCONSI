package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Entity representing a product in the inventory.
 * Contains pricing logic to sync net base price and gross price with VAT.
 */
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

    /** Name of the product. */
    @Column(nullable = false, length = 150)
    private String name;

    /** Short description/details of the product. */
    @Column(length = 255)
    private String description;

    /**
     * The gross price (VAT included) stored in the database.
     * Calculated as: base_price_net * (1 + taxRate.vatRate)
     */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    /**
     * The net base price (before VAT).
     * This is the primary value used for calculations.
     */
    @Column(name = "base_price_net", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal basePriceNet = BigDecimal.ZERO;

    /**
     * Returns the Gross Price (VAT included).
     * Calculated on the fly: basePriceNet * (1 + ivaRate)
     */
    public BigDecimal getPrice() {
        if (basePriceNet == null)
            return BigDecimal.ZERO;
        BigDecimal rate = taxRate != null && taxRate.getVatRate() != null ? taxRate.getVatRate()
                : BigDecimal.ZERO;
        return basePriceNet.multiply(BigDecimal.ONE.add(rate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Sets the Net price based on a Gross price input.
     * basePriceNet = grossPrice / (1 + ivaRate)
     * Also updates the persisted price field.
     * @param grossPrice The price including VAT.
     */
    public void setPrice(BigDecimal grossPrice) {
        if (grossPrice == null) {
            this.basePriceNet = BigDecimal.ZERO;
            this.price = BigDecimal.ZERO;
            return;
        }
        BigDecimal rate = taxRate != null && taxRate.getVatRate() != null ? taxRate.getVatRate()
                : BigDecimal.ZERO;
        this.basePriceNet = grossPrice.divide(BigDecimal.ONE.add(rate), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        this.price = grossPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Callback method to synchronize the price field before persist/update.
     * Ensures price = basePriceNet * (1 + taxRate.vatRate)
     */
    @PrePersist
    @PreUpdate
    private void syncPrice() {
        if (basePriceNet == null) {
            basePriceNet = BigDecimal.ZERO;
        }
        BigDecimal rate = taxRate != null && taxRate.getVatRate() != null ? taxRate.getVatRate()
                : BigDecimal.ZERO;
        this.price = basePriceNet.multiply(BigDecimal.ONE.add(rate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Available items in stock. */
    @Column(nullable = false, columnDefinition = "int default 0")
    @Builder.Default
    private Integer stock = 0;

    /** Whether the product is available for sale. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** URL for the product image. */
    @Column(length = 500)
    private String imageUrl;

    /** Category this product belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Tax rate applicable to this product. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_rate_id", nullable = false)
    private TaxRate taxRate;
}