package com.proconsi.electrobazar.util;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Utility component for calculating Spanish 'Recargo de Equivalencia' (RE) taxes.
 *
 * <p>The Recargo de Equivalencia is a special VAT regime in Spain that applies to
 * retailers (comerciantes minoristas) who are not entitled to deduct input VAT.
 * Instead, their suppliers charge them a surcharge on top of the standard VAT rate.</p>
 *
 * <p>Official RE rates (as per Spanish tax law - Ley 37/1992 del IVA, Art. 161):</p>
 * <ul>
 *   <li>21% VAT → 5.2% RE</li>
 *   <li>10% VAT → 1.4% RE</li>
 *   <li>4%  VAT → 0.5% RE</li>
 *   <li>2%  VAT → 0.15% RE (reduced rate for certain goods)</li>
 * </ul>
 *
 * <p>Calculation formula:</p>
 * <pre>
 *   Base Amount  = Unit Price × Quantity
 *   VAT Amount   = Base Amount × VAT Rate
 *   RE Amount    = Base Amount × RE Rate
 *   Total        = Base Amount + VAT Amount + RE Amount
 * </pre>
 *
 * <p>All monetary calculations use {@link RoundingMode#HALF_UP} with 2 decimal places.</p>
 */
@Component
public class RecargoEquivalenciaCalculator {

    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Mapping of VAT rates (as decimal fractions) to their corresponding RE rates.
     * Keys and values are stored as BigDecimal strings for precision.
     *
     * <p>Example: 0.21 (21% VAT) → 0.052 (5.2% RE)</p>
     */
    private static final Map<BigDecimal, BigDecimal> VAT_TO_RE_RATE_MAP = Map.of(
            new BigDecimal("0.21"),  new BigDecimal("0.052"),   // 21% VAT → 5.2% RE
            new BigDecimal("0.10"),  new BigDecimal("0.014"),   // 10% VAT → 1.4% RE
            new BigDecimal("0.04"),  new BigDecimal("0.005"),   // 4%  VAT → 0.5% RE
            new BigDecimal("0.02"),  new BigDecimal("0.0015")   // 2%  VAT → 0.15% RE
    );

    /**
     * Resolves the Recargo de Equivalencia rate for a given VAT rate.
     *
     * <p>The lookup uses exact BigDecimal comparison after normalizing the scale,
     * so both {@code new BigDecimal("0.21")} and {@code new BigDecimal("0.210")}
     * will correctly resolve to 5.2% RE.</p>
     *
     * @param vatRate the VAT rate as a decimal fraction (e.g., 0.21 for 21%)
     * @return the corresponding RE rate, or {@link BigDecimal#ZERO} if no mapping exists
     */
    public BigDecimal getRecargoRate(BigDecimal vatRate) {
        if (vatRate == null) {
            return BigDecimal.ZERO;
        }
        // Normalize scale for comparison (strip trailing zeros)
        BigDecimal normalizedVat = vatRate.stripTrailingZeros();
        return VAT_TO_RE_RATE_MAP.entrySet().stream()
                .filter(entry -> entry.getKey().stripTrailingZeros().compareTo(normalizedVat) == 0)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculates the full tax breakdown for a single line item.
     *
     * <p>If {@code applyRecargo} is false, the RE rate and amount will be zero.</p>
     *
     * @param productId   the product ID (for reference in the breakdown)
     * @param productName the product name (for display)
     * @param unitPrice   the base unit price (before taxes)
     * @param quantity    the number of units
     * @param vatRate     the VAT rate as a decimal fraction (e.g., 0.21)
     * @param applyRecargo whether to apply Recargo de Equivalencia
     * @return a fully populated {@link TaxBreakdown} record
     */
    public TaxBreakdown calculateLineBreakdown(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal vatRate,
            boolean applyRecargo) {

        // Base amount = unit price × quantity
        BigDecimal baseAmount = unitPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        // VAT amount = base × VAT rate
        BigDecimal vatAmount = baseAmount
                .multiply(vatRate)
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        // RE rate and amount
        BigDecimal recargoRate = applyRecargo ? getRecargoRate(vatRate) : BigDecimal.ZERO;
        BigDecimal recargoAmount = baseAmount
                .multiply(recargoRate)
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        // Total = base + VAT + RE
        BigDecimal totalAmount = baseAmount
                .add(vatAmount)
                .add(recargoAmount)
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        return TaxBreakdown.builder()
                .productId(productId)
                .productName(productName)
                .quantity(quantity)
                .unitPrice(unitPrice.setScale(MONETARY_SCALE, ROUNDING_MODE))
                .baseAmount(baseAmount)
                .vatRate(vatRate)
                .vatAmount(vatAmount)
                .recargoRate(recargoRate)
                .recargoAmount(recargoAmount)
                .totalAmount(totalAmount)
                .recargoApplied(applyRecargo && recargoRate.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    /**
     * Returns the complete VAT-to-RE rate mapping for informational purposes.
     *
     * @return an unmodifiable map of VAT rates to RE rates
     */
    public Map<BigDecimal, BigDecimal> getVatToReRateMap() {
        return VAT_TO_RE_RATE_MAP;
    }
}
