package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a pricing tariff that can be assigned to customers.
 */
@Entity
@Table(name = "tariffs", indexes = {
        @Index(name = "idx_tariffs_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tariff implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** System tariff name for standard retail prices. */
    public static final String MINORISTA = "MINORISTA";
    /** System tariff name for wholesale prices. */
    public static final String MAYORISTA = "MAYORISTA";
    /** System tariff name for employee discounts. */
    public static final String EMPLEADO = "EMPLEADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique name of the tariff. */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * Discount percentage applied to gross price (0-100).
     */
    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    /** Description of the tariff's usage. */
    @Column(length = 255)
    private String description;

    /** Whether the tariff is currently active. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Whether this is a system-defined tariff.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean systemTariff = false;

    /**
     * Returns true if this tariff applies a non-zero discount.
     */
    public boolean hasDiscount() {
        return discountPercentage != null
                && discountPercentage.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Human-readable label for selection menus. */
    public String getDisplayLabel() {
        if (hasDiscount()) {
            return name + " -" + discountPercentage.stripTrailingZeros().toPlainString() + "%";
        }
        return name;
    }
}
