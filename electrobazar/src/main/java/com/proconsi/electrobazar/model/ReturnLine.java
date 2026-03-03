package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a single product line within a return operation.
 * Links back to the original SaleLine to track exactly which line
 * is being returned and validates quantity limits there.
 */
@Entity
@Table(name = "return_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The return this line belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private SaleReturn saleReturn;

    /** The original sale line being returned. Non-nullable. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_line_id", nullable = false)
    private SaleLine saleLine;

    /** Number of units returned. Must be ≤ original SaleLine.quantity. */
    @Column(nullable = false)
    private Integer quantity;

    /** Unit price at the time of original sale (copied from SaleLine). */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** Subtotal for this return line = quantity × unitPrice. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    /**
     * VAT rate copied from SaleLine.vatRate at return time to ensure
     * historical accuracy even if the product rate changes later.
     */
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;
}
