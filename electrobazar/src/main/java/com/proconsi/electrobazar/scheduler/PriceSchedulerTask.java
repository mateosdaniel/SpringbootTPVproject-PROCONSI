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
 * Scheduled task for price transitions, IPC updates, and cache management.
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

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void verifyDailyPriceTransitions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        log.info("=== [PriceScheduler] Daily price verification started at {} ===", now.format(DATE_FORMATTER));

        try {
            BigDecimal ipc = ineApiService.getLatestIpc();
            BigDecimal lastAppliedIpc = getLastAppliedIpc();
            LocalDate lastAppliedIpcDate = getLastAppliedIpcDate();
            if (lastAppliedIpcDate != null) {
                log.debug("[PriceScheduler] Last IPC check was performed on {}", lastAppliedIpcDate.format(ONLY_DATE_FORMATTER));
            }

            if (ipc != null && lastAppliedIpc != null && ipc.compareTo(lastAppliedIpc) != 0) {
                BigDecimal increment = ipc.subtract(lastAppliedIpc);
                applyIpcPriceIncrease(increment, now);
                saveLastAppliedIpc(ipc, today);
            } else if (ipc != null && lastAppliedIpc == null) {
                saveLastAppliedIpc(ipc, today);
            }
        } catch (Exception e) {
            log.error("[PriceScheduler] Error handling IPC data: {}", e.getMessage());
        }

        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(23, 59, 59);
        List<ProductPrice> activatedToday = productPriceRepository.findPricesActivatedBetween(dayStart, dayEnd);
        
        if (!activatedToday.isEmpty()) {
            log.info("[PriceScheduler] {} price transitions active today.", activatedToday.size());
        }

        evictProductPricesCache();
        log.info("=== [PriceScheduler] Daily verification completed. ===");
    }

    private void applyIpcPriceIncrease(BigDecimal increment, LocalDateTime effectiveDate) {
        try {
            List<Long> activeProductIds = productService.findAllActive().stream()
                    .map(Product::getId)
                    .toList();

            if (!activeProductIds.isEmpty()) {
                BulkPriceUpdateRequest request = new BulkPriceUpdateRequest();
                request.setProductIds(activeProductIds);
                request.setPercentage(increment);
                request.setEffectiveDate(effectiveDate);
                request.setLabel("IPC Automatic Update (" + increment + "%)");
                productPriceService.bulkSchedulePrice(request);
            }
        } catch (Exception e) {
            log.error("[PriceScheduler] Failed IPC update: {}", e.getMessage());
        }
    }

    private void evictProductPricesCache() {
        var cache = cacheManager.getCache("productPrices");
        if (cache != null) {
            cache.clear();
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void logFuturePricesSummary() {
        LocalDateTime now = LocalDateTime.now();
        List<ProductPrice> futurePrices = productPriceRepository.findAllFuturePrices(now);
        if (!futurePrices.isEmpty()) {
            log.debug("[PriceScheduler Monitor] {} future price(s) scheduled.", futurePrices.size());
        }
    }
}
