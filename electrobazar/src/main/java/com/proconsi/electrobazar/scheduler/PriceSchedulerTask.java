package com.proconsi.electrobazar.scheduler;

import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.model.AppSetting;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.repository.AppSettingRepository;
import com.proconsi.electrobazar.repository.ProductPriceRepository;
import com.proconsi.electrobazar.service.IneApiService;
import com.proconsi.electrobazar.service.ProductPriceService;
import com.proconsi.electrobazar.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduled task component for daily price transition verification and cache
 * management.
 *
 * <p>
 * This scheduler runs daily at midnight (00:00:00 Europe/Madrid) to:
 * </p>
 * <ol>
 * <li>Fetch and cache the latest IPC from INE and apply incremental
 * updates.</li>
 * <li>Log all price transitions that became active today.</li>
 * <li>Log all upcoming price changes scheduled for the next 7 days.</li>
 * <li>Evict the entire {@code productPrices} cache.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceSchedulerTask {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter ONLY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String IPC_LAST_VALUE_KEY = "ipc_last_value";
    private static final String IPC_LAST_DATE_KEY = "ipc_last_date";

    private final ProductPriceRepository productPriceRepository;
    private final CacheManager cacheManager;
    private final IneApiService ineApiService;
    private final ProductService productService;
    private final ProductPriceService productPriceService;
    private final AppSettingRepository appSettingRepository;

    private BigDecimal getLastAppliedIpc() {
        return appSettingRepository.findByKey(IPC_LAST_VALUE_KEY)
                .map(setting -> new BigDecimal(setting.getValue()))
                .orElse(null);
    }

    private LocalDate getLastAppliedIpcDate() {
        return appSettingRepository.findByKey(IPC_LAST_DATE_KEY)
                .map(setting -> LocalDate.parse(setting.getValue()))
                .orElse(null);
    }

    private void saveLastAppliedIpc(BigDecimal ipc, LocalDate date) {
        if (ipc != null) {
            AppSetting valueSetting = appSettingRepository.findByKey(IPC_LAST_VALUE_KEY)
                    .orElse(AppSetting.builder().key(IPC_LAST_VALUE_KEY).build());
            valueSetting.setValue(ipc.toString());
            appSettingRepository.save(valueSetting);
        }
        if (date != null) {
            AppSetting dateSetting = appSettingRepository.findByKey(IPC_LAST_DATE_KEY)
                    .orElse(AppSetting.builder().key(IPC_LAST_DATE_KEY).build());
            dateSetting.setValue(date.toString());
            appSettingRepository.save(dateSetting);
        }
    }

    /**
     * Daily midnight task: manages IPC, verifies transitions, and evicts cache.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void verifyDailyPriceTransitions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        log.info("=== [PriceScheduler] Daily price verification started at {} ===",
                now.format(DATE_FORMATTER));

        // ── 0. IPC Management ──────────────────────────────────────────────────
        try {
            BigDecimal ipc = ineApiService.getLatestIpc();
            BigDecimal lastAppliedIpc = getLastAppliedIpc();
            LocalDate lastAppliedIpcDate = getLastAppliedIpcDate();

            if (ipc == null) {
                log.warn("[PriceScheduler] IPC fetch returned null. Automatic price update skipped.");
            } else if (lastAppliedIpc == null) {
                // First run ever
                saveLastAppliedIpc(ipc, today);
                log.info("First IPC reading: {}%. Baseline stored, no price update on first run.", ipc);
            } else if (ipc.compareTo(lastAppliedIpc) == 0) {
                log.info("IPC unchanged ({}%), no price update needed", ipc);
            } else {
                // IPC changed: calculate incremental increase
                BigDecimal increment = ipc.subtract(lastAppliedIpc);
                String lastDateStr = lastAppliedIpcDate != null ? lastAppliedIpcDate.format(ONLY_DATE_FORMATTER)
                        : "N/A";

                log.info("IPC changed from {}% to {}% (last update: {}), applying {}% increment to all products.",
                        lastAppliedIpc, ipc, lastDateStr, increment);

                applyIpcPriceIncrease(increment, now);
                saveLastAppliedIpc(ipc, today);
            }
        } catch (Exception e) {
            log.error("[PriceScheduler] Error handling IPC data in daily task: {}", e.getMessage());
        }

        // ── 1. Log prices that became active today ─────────────────────────────
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(23, 59, 59);

        List<ProductPrice> activatedToday = productPriceRepository.findPricesActivatedBetween(dayStart, dayEnd);

        if (activatedToday.isEmpty()) {
            log.info("[PriceScheduler] No price transitions scheduled for today ({}).",
                    today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        } else {
            log.info("[PriceScheduler] {} price transition(s) active today:",
                    activatedToday.size());
            activatedToday.forEach(p -> log.info(
                    "  \u2192 Product: '{}' (id={}) | New price: {} \u20ac | VAT: {}% | Effective: {} | Label: {}",
                    p.getProduct().getName(),
                    p.getProduct().getId(),
                    p.getPrice(),
                    p.getVatRate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString(),
                    p.getStartDate().format(DATE_FORMATTER),
                    p.getLabel() != null ? p.getLabel() : "N/A"));
        }

        // ── 2. Log upcoming price changes in the next 7 days ──────────────────
        LocalDateTime sevenDaysFromNow = now.plusDays(7);
        List<ProductPrice> upcomingPrices = productPriceRepository.findAllFuturePrices(now)
                .stream()
                .filter(p -> p.getStartDate().isBefore(sevenDaysFromNow))
                .toList();

        if (!upcomingPrices.isEmpty()) {
            log.info("[PriceScheduler] {} upcoming price change(s) in the next 7 days:",
                    upcomingPrices.size());
            upcomingPrices.forEach(p -> log.info(
                    "  \u23f0 Product: '{}' (id={}) | Price: {} \u20ac | Effective: {} | Label: {}",
                    p.getProduct().getName(),
                    p.getProduct().getId(),
                    p.getPrice(),
                    p.getStartDate().format(DATE_FORMATTER),
                    p.getLabel() != null ? p.getLabel() : "N/A"));
        }

        // ── 3. Evict cache ─────────────────────────────────────────────────────
        evictProductPricesCache();

        log.info("=== [PriceScheduler] Daily verification completed. Cache evicted. ===");
    }

    /**
     * Applies the bulk percentage increase (increment) to all active products.
     */
    private void applyIpcPriceIncrease(BigDecimal increment, LocalDateTime effectiveDate) {
        try {
            List<Long> activeProductIds = productService.findAllActive().stream()
                    .map(Product::getId)
                    .toList();

            if (activeProductIds.isEmpty()) {
                log.info("[PriceScheduler] No active products found for IPC update.");
                return;
            }

            BulkPriceUpdateRequest request = new BulkPriceUpdateRequest();
            request.setProductIds(activeProductIds);
            request.setPercentage(increment);
            request.setEffectiveDate(effectiveDate);
            request.setLabel("Actualización automática IPC (Incremento: " + increment + "%)");

            productPriceService.bulkSchedulePrice(request);
            log.info("[PriceScheduler] Successfully applied {}% increment to {} products.",
                    increment, activeProductIds.size());
        } catch (Exception e) {
            log.error("[PriceScheduler] CRITICAL: Failed to apply bulk IPC price increase: {}", e.getMessage());
        }
    }

    /**
     * Evicts all entries from the {@code productPrices} cache.
     */
    private void evictProductPricesCache() {
        var cache = cacheManager.getCache("productPrices");
        if (cache != null) {
            cache.clear();
            log.info("[PriceScheduler] 'productPrices' cache cleared successfully.");
        } else {
            log.warn("[PriceScheduler] Cache 'productPrices' not found in CacheManager.");
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void logFuturePricesSummary() {
        LocalDateTime now = LocalDateTime.now();
        List<ProductPrice> futurePrices = productPriceRepository.findAllFuturePrices(now);

        if (!futurePrices.isEmpty()) {
            log.debug("[PriceScheduler] {} future price(s) scheduled across all products.",
                    futurePrices.size());
        }
    }

}
