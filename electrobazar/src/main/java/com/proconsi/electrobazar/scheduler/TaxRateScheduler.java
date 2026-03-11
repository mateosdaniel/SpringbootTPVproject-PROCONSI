package com.proconsi.electrobazar.scheduler;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.TariffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxRateScheduler {

    private final TaxRateRepository taxRateRepository;
    private final ProductService productService;
    private final TariffService tariffService;

    /**
     * Checks daily at 00:01 if there are any new TaxRates that should be applied starting today.
     * Cron: "0 1 0 * * *" (Seconds Minutes Hours Day Month DayOfWeek)
     */
    @Scheduled(cron = "0 1 0 * * *")
    public void autoApplyTaxRates() {
        LocalDate today = LocalDate.now();
        log.info("Checking for new TaxRates starting today ({})", today);

        List<TaxRate> newRates = taxRateRepository.findByValidFromAndActiveTrue(today);

        if (newRates.isEmpty()) {
            log.info("No new TaxRates to apply today.");
            return;
        }

        for (TaxRate rate : newRates) {
            try {
                log.info("Auto-applying TaxRate: {} ({}%)", rate.getDescription(), rate.getVatRate());
                productService.applyNewTaxRate(rate.getId());
                log.info("Successfully applied TaxRate: {}", rate.getDescription());

                // Regenerate tariff_price_history for all products that now use this new VAT rate
                List<Product> affectedProducts = productService.findAllActiveWithCategory().stream()
                        .filter(p -> p.getTaxRate() != null && rate.getId().equals(p.getTaxRate().getId()))
                        .toList();

                if (!affectedProducts.isEmpty()) {
                    log.info("Regenerating tariff price history for {} affected products.", affectedProducts.size());
                    tariffService.regenerateTariffHistoryForProducts(affectedProducts);
                } else {
                    log.info("No active products found with the new TaxRate id={} after applying.", rate.getId());
                }

            } catch (Exception e) {
                log.error("Error auto-applying TaxRate {}: {}", rate.getId(), e.getMessage());
            }
        }
    }
}
