package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a temporal price entry for a product (Price History Pattern).
 * A product can have multiple price records over time, each with a validity
 * window
 * defined by startDate and endDate. A null endDate means the price is currently
 * active
 * with no scheduled expiry.
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
     * The price value. Uses BigDecimal for monetary precision.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * The VAT rate applicable to this product price (e.g., 0.21 for 21%).
     * Stored as a decimal fraction.
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
     * A null value means this price has no scheduled end date (currently active).
     */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /**
     * Optional label for this price entry (e.g., "Tarifa 2025", "Oferta Verano").
     */
    @Column(length = 100)
    private String label;

    /**
     * Timestamp when this price record was created (audit field).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
