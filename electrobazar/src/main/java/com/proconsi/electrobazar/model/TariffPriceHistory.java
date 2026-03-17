package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Historical record of pricing for a specific product and tariff combination.
 * Keeps track of how prices evolved over time.
 */
@Entity
@Table(name = "tariff_price_history", indexes = {
    @Index(name = "idx_tph_product", columnList = "product_id"),
    @Index(name = "idx_tph_tariff", columnList = "tariff_id"),
    @Index(name = "idx_tph_dates", columnList = "valid_from, valid_to")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TariffPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Affected product. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Affected tariff. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id", nullable = false)
    private Tariff tariff;

    /** Base price from the product catalogue. */
    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    /** Net price after applying the tariff discount. */
    @Column(name = "net_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal netPrice;

    /** VAT rate at the time of snapshot. */
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    /** Final price including VAT. */
    @Column(name = "price_with_vat", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceWithVat;

    /** Recargo de Equivalencia rate at snapshot time. */
    @Column(name = "re_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal reRate;

    /** Final price including VAT and RE. */
    @Column(name = "price_with_re", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceWithRe;

    /** Discount percentage applied by the tariff. */
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Date from which these prices were valid. */
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Date until which these prices were valid (inclusive). */
    @Column(name = "valid_to")
    private LocalDate validTo;

    /** Documentation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}


