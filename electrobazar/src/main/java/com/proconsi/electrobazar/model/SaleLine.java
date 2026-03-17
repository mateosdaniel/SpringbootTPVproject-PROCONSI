package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Entity representing a single line item in a sale.
 * Tracks pricing, taxes, and discounts at the time of the transaction.
 */
@Entity
@Table(name = "sale_lines", indexes = {
        @Index(name = "idx_sale_lines_sale_id", columnList = "sale_id"),
        @Index(name = "idx_sale_lines_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The sale this line belongs to. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    /** The product sold. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Number of units sold. */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * Gross unit price AFTER applying the tariff discount.
     * What the customer pays per unit (VAT included).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Gross unit price BEFORE applying any tariff discount (catalogue price).
     */
    @Column(nullable = false, precision = 10, scale = 2, name = "original_unit_price")
    @Builder.Default
    private BigDecimal originalUnitPrice = BigDecimal.ZERO;

    /**
     * Discount percentage applied specifically to this line.
     */
    @Column(nullable = false, precision = 5, scale = 2, name = "discount_percentage")
    @Builder.Default
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    /** Net unit base price (before tax). */
    @Column(nullable = false, precision = 10, scale = 2, name = "base_price_net")
    @Builder.Default
    private BigDecimal basePriceNet = BigDecimal.ZERO;

    /** VAT rate at the time of sale. */
    @Column(nullable = false, precision = 5, scale = 4, name = "vat_rate")
    private BigDecimal vatRate;

    /** Gross subtotal for this line (quantity * unitPrice). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    /** Total taxable base for this line. */
    @Column(nullable = false, precision = 10, scale = 2, name = "base_amount")
    private BigDecimal baseAmount;

    /** Total VAT amount for this line. */
    @Column(nullable = false, precision = 10, scale = 2, name = "vat_amount")
    private BigDecimal vatAmount;

    /** Recargo de Equivalencia rate applied to this line. */
    @Column(nullable = false, precision = 5, scale = 4, name = "recargo_rate")
    private BigDecimal recargoRate;

    /** Total RE amount for this line. */
    @Column(nullable = false, precision = 10, scale = 2, name = "recargo_amount")
    private BigDecimal recargoAmount;
}