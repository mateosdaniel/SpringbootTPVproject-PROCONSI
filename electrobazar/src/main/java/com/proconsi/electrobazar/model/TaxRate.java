package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity representing a VAT rate and its associated Recargo de Equivalencia.
 * Includes a validity period to track historical tax changes.
 */
@Entity
@Table(name = "tax_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Standard VAT rate (e.g., 0.21 for 21%). */
    @NotNull(message = "El tipo de IVA es obligatorio")
    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    /** Recargo de Equivalencia rate (e.g., 0.052 for 5.2%). */
    @NotNull(message = "El recargo de equivalencia es obligatorio")
    @Column(name = "re_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal reRate;

    /** Optional label or description (e.g., "General", "Reduced"). */
    @Column(length = 100)
    private String description;

    /** Whether this tax rate can currently be assigned to products. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    /** Date from which this tax rate is applicable. */
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Date until which this tax rate is applicable (inclusive). */
    @Column(name = "valid_to")
    private LocalDate validTo;
}
