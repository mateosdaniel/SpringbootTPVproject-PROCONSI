package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Represents a temporal price entry for a product (Price History Pattern).
 * A product can have multiple price records over time.
 */
@Entity
@Table(name = "product_prices", indexes = {
        @Index(name = "idx_product_prices_product_id", columnList = "product_id"),
        @Index(name = "idx_product_prices_start_date", columnList = "start_date"),
        @Index(name = "idx_product_prices_end_date", columnList = "end_date"),
        @Index(name = "idx_product_prices_lookup", columnList = "product_id, start_date, end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The product this price record belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * The net base price (before VAT).
     */
    @Column(name = "base_price_net", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal basePriceNet = BigDecimal.ZERO;

    /**
     * The Gross Price (VAT included).
     */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Sets the gross price and calculates the net base price accordingly.
     * @param grossPrice The price including VAT.
     */
    public void setPrice(BigDecimal grossPrice) {
        this.price = grossPrice;
        if (grossPrice == null) {
            this.basePriceNet = BigDecimal.ZERO;
            return;
        }
        BigDecimal rate = vatRate != null ? vatRate : new BigDecimal("0.21");
        this.basePriceNet = grossPrice.divide(BigDecimal.ONE.add(rate), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The VAT rate applicable to this product price (e.g., 0.21 for 21%).
     */
    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("0.21");

    /**
     * The date and time from which this price is valid (inclusive).
     */
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    /**
     * The date and time until which this price is valid (inclusive).
     * A null value means this price is currently active with no scheduled end.
     */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * Optional label for this price entry (e.g., "Summer Sale").
     */
    @Column(length = 100)
    private String label;

    /**
     * Timestamp when this price record was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
