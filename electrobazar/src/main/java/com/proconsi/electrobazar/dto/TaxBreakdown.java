package com.proconsi.electrobazar.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Represents the detailed tax breakdown for a single sale line item.
 * Supports both standard VAT and the Spanish 'Recargo de Equivalencia' (RE).
 *
 * <p>
 * Convention: Prices are stored VAT-inclusive. Net prices are derived for
 * fiscal reporting.
 * </p>
 *
 * <p>
 * Formula: Net = Gross / (1 + VAT%), Total = Net + (Net × VAT%) + (Net × RE%)
 * </p>
 *
 * <p>
 * Spanish RE rates mapped to VAT rates:
 * </p>
 * <ul>
 * <li>21% VAT → 5.2% RE</li>
 * <li>10% VAT → 1.4% RE</li>
 * <li>4% VAT → 0.5% RE</li>
 * <li>2% VAT → 0.15% RE</li>
 * </ul>
 */
@Data
@Builder
public class TaxBreakdown {

    /** The product ID this breakdown refers to. */
    private Long productId;

    /** The product name for display purposes. */
    private String productName;

    /** Quantity of units. */
    private Integer quantity;

    /** Unit price (base price before taxes). */
    private BigDecimal unitPrice;

    /** Total base amount = unitPrice × quantity. */
    private BigDecimal baseAmount;

    /** VAT rate as a decimal fraction (e.g., 0.21 for 21%). */
    private BigDecimal vatRate;

    /** VAT amount = baseAmount × vatRate. */
    private BigDecimal vatAmount;

    /**
     * Recargo de Equivalencia rate as a decimal fraction (e.g., 0.052 for 5.2%).
     * Zero if the customer does not have RE.
     */
    private BigDecimal recargoRate;

    /**
     * Recargo de Equivalencia amount = baseAmount × recargoRate.
     * Zero if the customer does not have RE.
     */
    private BigDecimal recargoAmount;

    /** Total line amount = baseAmount + vatAmount + recargoAmount. */
    private BigDecimal totalAmount;

    /** Whether Recargo de Equivalencia was applied. */
    private boolean recargoApplied;
}
