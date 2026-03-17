package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.TariffPriceHistory;
import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;
import java.time.LocalDate;
import java.util.List;

/**
 * Interface for querying historical pricing data tied to specific tariffs.
 */
public interface TariffPriceHistoryService {

    /**
     * Retrieves the price evolution for a whole tariff.
     * @param tariffId ID of the tariff.
     * @return List of historical price records.
     */
    List<TariffPriceHistory> getHistoryByTariff(Long tariffId);

    /**
     * Retrieves price changes for a specific product across all tariffs.
     * @param productId ID of the product.
     * @return List of historical price records.
     */
    List<TariffPriceHistory> getHistoryByProduct(Long productId);

    /**
     * Retrieves the prices currently in effect for a given tariff.
     * @param tariffId ID of the tariff.
     * @return List of active price DTOs.
     */
    List<TariffPriceEntryDTO> getCurrentPricesForTariff(Long tariffId);

    /**
     * Lists distinct dates when price changes were applied to a tariff.
     * @param tariffId ID of the tariff.
     * @return List of LocalDate objects.
     */
    List<LocalDate> getDistinctValidFromDates(Long tariffId);

    /**
     * Snapshots the prices for a tariff as they were on a specific date.
     * @param tariffId ID of the tariff.
     * @param date     Point-in-time calculation date.
     * @return List of prices for that date.
     */
    List<TariffPriceEntryDTO> getPricesForTariffAtDate(Long tariffId, LocalDate date);
}
