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

/**
 * Scheduled task for official Tax Rate transitions.
 * Ensures that whenever a new VAT or RE rate is scheduled to take effect,
 * the entire product catalog and price historian are updated automatically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxRateScheduler {

    private final TaxRateRepository taxRateRepository;
    private final ProductService productService;
    private final TariffService tariffService;

    /**
     * Daily check for new TaxRates starting today.
     * Runs every day at 00:01 AM.
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

                List<Product> affectedProducts = productService.findAllActiveWithCategory().stream()
                        .filter(p -> p.getTaxRate() != null && rate.getId().equals(p.getTaxRate().getId()))
                        .toList();

                if (!affectedProducts.isEmpty()) {
                    log.info("Regenerating tariff price history for {} affected products.", affectedProducts.size());
                    tariffService.regenerateTariffHistoryForProducts(affectedProducts);
                }
            } catch (Exception e) {
                log.error("Error auto-applying TaxRate {}: {}", rate.getId(), e.getMessage());
            }
        }
    }
}
