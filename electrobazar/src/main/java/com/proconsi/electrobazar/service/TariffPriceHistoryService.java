package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.TariffPriceHistory;
import java.util.List;

import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;

public interface TariffPriceHistoryService {
    List<TariffPriceHistory> getHistoryByTariff(Long tariffId);
    List<TariffPriceHistory> getHistoryByProduct(Long productId);
    List<TariffPriceEntryDTO> getCurrentPricesForTariff(Long tariffId);
    List<java.time.LocalDate> getDistinctValidFromDates(Long tariffId);
    List<TariffPriceEntryDTO> getPricesForTariffAtDate(Long tariffId, java.time.LocalDate date);
}
