package com.proconsi.electrobazar.util;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility component for calculating Spanish 'Recargo de Equivalencia' (RE)
 * taxes.
 *
 * <p>
 * The Recargo de Equivalencia is a special VAT regime in Spain that applies to
 * retailers (comerciantes minoristas) who are not entitled to deduct input VAT.
 * Instead, their suppliers charge them a surcharge on top of the standard VAT
 * rate.
 * </p>
 *
 * <p>
 * Official RE rates (as per Spanish tax law - Ley 37/1992 del IVA, Art. 161):
 * </p>
 * <ul>
 * <li>21% VAT → 5.2% RE</li>
 * <li>10% VAT → 1.4% RE</li>
 * <li>4% VAT → 0.5% RE</li>
 * <li>2% VAT → 0.15% RE (reduced rate for certain goods)</li>
 * </ul>
 *
 * <p>
 * Calculation formula:
 * </p>
 * 
 * <pre>
 *   Base Amount  = Unit Price × Quantity
 *   VAT Amount   = Base Amount × VAT Rate
 *   RE Amount    = Base Amount × RE Rate
 *   Total        = Base Amount + VAT Amount + RE Amount
 * </pre>
 *
 * <p>
 * All monetary calculations use {@link RoundingMode#HALF_UP} with 2 decimal
 * places.
 * </p>
 */
@Component
public class RecargoEquivalenciaCalculator {

        private static final int MONETARY_SCALE = 2;
        private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

        @Autowired
        private TaxRateRepository taxRateRepository;

        /**
         * Dynamically builds the mapping of VAT rates to their corresponding RE rates
         * from the database.
         *
         * @return a map of VAT rates (decimal) to RE rates (decimal)
         */
        private Map<BigDecimal, BigDecimal> getVatToReRateMapDynamic() {
                return taxRateRepository.findByActiveTrue().stream()
                                .collect(Collectors.toMap(TaxRate::getVatRate, TaxRate::getReRate));
        }

        /**
         * Resolves the Recargo de Equivalencia rate for a given VAT rate.
         *
         * <p>
         * The lookup uses exact BigDecimal comparison after normalizing the scale,
         * so both {@code new BigDecimal("0.21")} and {@code new BigDecimal("0.210")}
         * will correctly resolve to 5.2% RE.
         * </p>
         *
         * @param vatRate the VAT rate as a decimal fraction (e.g., 0.21 for 21%)
         * @return the corresponding RE rate, or {@link BigDecimal#ZERO} if no mapping
         *         exists
         */
        public BigDecimal getRecargoRate(BigDecimal vatRate) {
                if (vatRate == null) {
                        return BigDecimal.ZERO;
                }
                // Normalize scale for comparison (strip trailing zeros)
                BigDecimal normalizedVat = vatRate.stripTrailingZeros();
                return getVatToReRateMapDynamic().entrySet().stream()
                                .filter(entry -> entry.getKey().stripTrailingZeros().compareTo(normalizedVat) == 0)
                                .map(Map.Entry::getValue)
                                .findFirst()
                                .orElse(BigDecimal.ZERO);
        }

        /**
         * Calculates the full tax breakdown for a single line item starting from a
         * Gross Price.
         *
         * <p>
         * Convention: Prices are stored VAT-inclusive. Net prices are derived for
         * fiscal reporting.
         * </p>
         *
         * @param productId    the product ID (for reference in the breakdown)
         * @param productName  the product name (for display)
         * @param grossPrice   the unit price including VAT (Gross Price)
         * @param quantity     the number of units
         * @param vatRate      the VAT rate as a decimal fraction (e.g., 0.21)
         * @param applyRecargo whether to apply Recargo de Equivalencia
         * @return a fully populated {@link TaxBreakdown} record
         */
        public TaxBreakdown calculateLineBreakdown(
                        Long productId,
                        String productName,
                        BigDecimal grossPrice,
                        Integer quantity,
                        BigDecimal vatRate,
                        boolean applyRecargo) {

                // Formula: Net = Gross / (1 + VAT_Rate)
                BigDecimal divisor = BigDecimal.ONE.add(vatRate);

                // Use 10 decimal places for intermediate calculation to ensure precision
                BigDecimal netPrice = grossPrice.divide(divisor, 10, ROUNDING_MODE);

                // Base amount = Net Unit Price × quantity
                BigDecimal baseAmount = netPrice
                                .multiply(BigDecimal.valueOf(quantity))
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                // VAT amount = base × VAT rate
                // We calculate VAT from the base for fiscal integrity
                BigDecimal vatAmount = baseAmount
                                .multiply(vatRate)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                // RE rate and amount
                BigDecimal recargoRate = applyRecargo ? getRecargoRate(vatRate) : BigDecimal.ZERO;
                BigDecimal recargoAmount = baseAmount
                                .multiply(recargoRate)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                // Total = base + VAT + RE
                // Note: For standard customers (applyRecargo=false), this should perfectly
                // match Gross × Quantity
                // after rounding. For RE customers, it adds the surcharge on top of the base.
                BigDecimal totalAmount = baseAmount
                                .add(vatAmount)
                                .add(recargoAmount)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                return TaxBreakdown.builder()
                                .productId(productId)
                                .productName(productName)
                                .quantity(quantity)
                                .unitPrice(netPrice.setScale(MONETARY_SCALE, ROUNDING_MODE)) // Return Net Unit Price
                                                                                             // for reporting
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
                return getVatToReRateMapDynamic();
        }
}
