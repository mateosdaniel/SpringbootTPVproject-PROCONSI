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
 * Utility component for calculating Spanish 'Recargo de Equivalencia' (RE) taxes.
 * 
 * The Recargo de Equivalencia is a special VAT regime in Spain that applies to 
 * retailers who cannot deduct input VAT. Their suppliers charge them a surcharge 
 * on top of the standard VAT rate.
 * 
 * Official RE rates (Spanish Law 37/1992):
 * - 21% VAT -> 5.2% RE
 * - 10% VAT -> 1.4% RE
 * - 4% VAT -> 0.5% RE
 * 
 * Calculation formula used:
 * Base Amount = Unit Price * Quantity
 * VAT Amount = Base Amount * VAT Rate
 * RE Amount = Base Amount * RE Rate
 * Total = Base Amount + VAT Amount + RE Amount
 */
@Component
public class RecargoEquivalenciaCalculator {

    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private TaxRateRepository taxRateRepository;

    /**
     * Builds the mapping of VAT rates to RE rates from the database.
     */
    private Map<BigDecimal, BigDecimal> getVatToReRateMapDynamic() {
        return taxRateRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(
                    TaxRate::getVatRate, 
                    TaxRate::getReRate, 
                    (existing, replacement) -> replacement
                ));
    }

    /**
     * Resolves the RE rate for a given VAT rate.
     * Normalized scale comparison ensures 0.21 and 0.210 match correctly.
     */
    public BigDecimal getRecargoRate(BigDecimal vatRate) {
        if (vatRate == null) return BigDecimal.ZERO;
        
        BigDecimal normalizedVat = vatRate.stripTrailingZeros();
        return getVatToReRateMapDynamic().entrySet().stream()
                .filter(entry -> entry.getKey().stripTrailingZeros().compareTo(normalizedVat) == 0)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculates the full tax breakdown for a single item starting from a Gross Price.
     * Prices in TPV are stored VAT-inclusive (Gross).
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
        BigDecimal netPrice = grossPrice.divide(divisor, 10, ROUNDING_MODE);

        // Base amount = Net Unit Price * quantity
        BigDecimal baseAmount = netPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        // VAT amount = base * VAT rate
        BigDecimal vatAmount = baseAmount
                .multiply(vatRate)
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        // RE rate and amount
        BigDecimal recargoRate = applyRecargo ? getRecargoRate(vatRate) : BigDecimal.ZERO;
        BigDecimal recargoAmount = baseAmount
                .multiply(recargoRate)
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        // Final Total
        BigDecimal totalAmount = baseAmount.add(vatAmount).add(recargoAmount)
                .setScale(MONETARY_SCALE, ROUNDING_MODE);

        return TaxBreakdown.builder()
                .productId(productId)
                .productName(productName)
                .quantity(quantity)
                .unitPrice(netPrice.setScale(MONETARY_SCALE, ROUNDING_MODE))
                .baseAmount(baseAmount)
                .vatRate(vatRate)
                .vatAmount(vatAmount)
                .recargoRate(recargoRate)
                .recargoAmount(recargoAmount)
                .totalAmount(totalAmount)
                .recargoApplied(applyRecargo && recargoRate.compareTo(BigDecimal.ZERO) > 0)
                .build();
    }

    public Map<BigDecimal, BigDecimal> getVatToReRateMap() {
        return getVatToReRateMapDynamic();
    }
}


