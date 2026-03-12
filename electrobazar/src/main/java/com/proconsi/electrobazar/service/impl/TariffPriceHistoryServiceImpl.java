package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.TariffPriceHistory;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.service.TariffPriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;
import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TariffPriceHistoryServiceImpl implements TariffPriceHistoryService {

        private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
        private final com.proconsi.electrobazar.repository.ProductRepository productRepository;
        private final com.proconsi.electrobazar.repository.TariffRepository tariffRepository;
        private final com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator recargoCalculator;

        @Override
        public List<TariffPriceHistory> getHistoryByTariff(Long tariffId) {
                return tariffPriceHistoryRepository.findByTariffIdOrderByValidFromDesc(tariffId);
        }

        @Override
        public List<TariffPriceHistory> getHistoryByProduct(Long productId) {
                return tariffPriceHistoryRepository.findByProductIdOrderByValidFromDesc(productId);
        }

        @Override
        public List<java.time.LocalDate> getDistinctValidFromDates(Long tariffId) {
                List<java.time.LocalDate> dates = tariffPriceHistoryRepository.findDistinctValidFromByTariffId(tariffId);
                log.info("Distinct dates for tariff {}: {}", tariffId, dates);
                return dates;
        }

        @Override
        public List<TariffPriceEntryDTO> getCurrentPricesForTariff(Long tariffId) {
                return getPricesForTariffAtDate(tariffId, LocalDate.now());
        }

        @Override
        public List<TariffPriceEntryDTO> getPricesForTariffAtDate(Long tariffId, LocalDate date) {
                // Find history records exactly on the specified date
                List<TariffPriceHistory> histories = tariffPriceHistoryRepository.findByTariffIdAndValidFrom(tariffId, date);

                return histories.stream()
                                .map(h -> TariffPriceEntryDTO.builder()
                                                .productId(h.getProduct().getId())
                                                .productName(h.getProduct().getName())
                                                .categoryName(h.getProduct().getCategory() != null
                                                                ? h.getProduct().getCategory().getName()
                                                                : "Sin Categoría")
                                                .basePrice(h.getProduct().getPrice())
                                                .netPrice(h.getNetPrice())
                                                .vatRate(h.getVatRate())
                                                .priceWithVat(h.getPriceWithVat())
                                                .reRate(h.getReRate())
                                                .priceWithRe(h.getPriceWithRe() != null ? h.getPriceWithRe() : h.getPriceWithVat())
                                                .vatAmount(h.getPriceWithVat().subtract(h.getNetPrice()))
                                                .reAmount(h.getPriceWithRe() != null ? h.getPriceWithRe().subtract(h.getPriceWithVat()) : BigDecimal.ZERO)
                                                .discountPercent(h.getDiscountPercent())
                                                .validFrom(h.getValidFrom())
                                                .validTo(h.getValidTo())
                                                .isFromHistory(true)
                                                .build())
                                .collect(Collectors.toList());
        }
}
