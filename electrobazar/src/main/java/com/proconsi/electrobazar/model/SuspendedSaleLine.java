package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * One product line within a suspended sale.
 * Stores the product, quantity and unit price at the time of suspension
 * so the cart can be fully reconstructed when resumed.
 */
@Entity
@Table(name = "suspended_sale_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuspendedSaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The suspended sale this line belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suspended_sale_id", nullable = false)
    private SuspendedSale suspendedSale;

    /** The product in this line. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Quantity of units in the cart at suspension time. */
    @Column(nullable = false)
    private Integer quantity;

    /** Unit price at suspension time (could differ from current price later). */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
}
