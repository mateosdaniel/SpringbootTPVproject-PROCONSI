package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.TariffPriceHistory;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.service.TariffPriceHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Tariff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

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
        public List<TariffPriceEntryDTO> getCurrentPricesForTariff(Long tariffId) {
                Tariff tariff = tariffRepository.findById(tariffId)
                                .orElseThrow(() -> new RuntimeException("Tarifa no encontrada"));
                List<Product> products = productRepository.findAllActiveWithCategory();

                List<TariffPriceHistory> histories = tariffPriceHistoryRepository
                                .findByTariffIdOrderByValidFromDesc(tariffId).stream()
                                .filter(t -> t.getValidTo() == null)
                                .collect(Collectors.toList());

                Map<Long, TariffPriceHistory> historyMap = histories.stream()
                                .collect(Collectors.toMap(h -> h.getProduct().getId(), h -> h,
                                                (existing, replacement) -> existing));

                BigDecimal discountPercent = tariff.getDiscountPercentage() != null ? tariff.getDiscountPercentage()
                                : BigDecimal.ZERO;
                BigDecimal discountMultiplier = BigDecimal.ONE
                                .subtract(discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));

                List<TariffPriceEntryDTO> results = new ArrayList<>();
                for (Product product : products) {
                        TariffPriceHistory h = historyMap.get(product.getId());

                        // Base price for display is always product.price (single source of truth)
                        BigDecimal grossPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;

                        if (h != null) {
                                // ── History record found: use stored prices directly, no recalculation ──
                                BigDecimal netPrice    = h.getNetPrice();
                                BigDecimal priceWithVat = h.getPriceWithVat();
                                BigDecimal priceWithRe  = h.getPriceWithRe() != null ? h.getPriceWithRe() : priceWithVat;
                                BigDecimal vatRate      = h.getVatRate();
                                BigDecimal reRate       = h.getReRate();
                                BigDecimal discPct      = h.getDiscountPercent();

                                BigDecimal vatAmount = priceWithVat.subtract(netPrice);
                                BigDecimal reAmount  = priceWithRe.subtract(priceWithVat);

                                results.add(TariffPriceEntryDTO.builder()
                                                .productId(product.getId())
                                                .productName(product.getName())
                                                .categoryName(product.getCategory() != null
                                                                ? product.getCategory().getName()
                                                                : "Sin Categoría")
                                                .basePrice(grossPrice)      // product.price — display only
                                                .netPrice(netPrice)
                                                .vatRate(vatRate)
                                                .priceWithVat(priceWithVat)
                                                .reRate(reRate)
                                                .priceWithRe(priceWithRe)
                                                .vatAmount(vatAmount)
                                                .reAmount(reAmount)
                                                .discountPercent(discPct)
                                                .validFrom(h.getValidFrom())
                                                .validTo(h.getValidTo())
                                                .isFromHistory(true)
                                                .build());
                        } else {
                                // ── No history: calculate from product.price + tariff discount ──
                                BigDecimal vatRate = product.getTaxRate() != null
                                                && product.getTaxRate().getVatRate() != null
                                                                ? product.getTaxRate().getVatRate()
                                                                : new BigDecimal("0.21");
                                BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);

                                BigDecimal productNet = grossPrice
                                                .divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP);
                                BigDecimal netPrice = productNet.multiply(discountMultiplier)
                                                .setScale(2, RoundingMode.HALF_UP);

                                BigDecimal priceWithVat = netPrice.multiply(BigDecimal.ONE.add(vatRate))
                                                .setScale(2, RoundingMode.HALF_UP);
                                BigDecimal priceWithRe  = netPrice.multiply(BigDecimal.ONE.add(vatRate).add(reRate))
                                                .setScale(2, RoundingMode.HALF_UP);

                                BigDecimal vatAmount = priceWithVat.subtract(netPrice);
                                BigDecimal reAmount  = priceWithRe.subtract(priceWithVat);

                                results.add(TariffPriceEntryDTO.builder()
                                                .productId(product.getId())
                                                .productName(product.getName())
                                                .categoryName(product.getCategory() != null
                                                                ? product.getCategory().getName()
                                                                : "Sin Categoría")
                                                .basePrice(grossPrice)
                                                .netPrice(netPrice)
                                                .vatRate(vatRate)
                                                .priceWithVat(priceWithVat)
                                                .reRate(reRate)
                                                .priceWithRe(priceWithRe)
                                                .vatAmount(vatAmount)
                                                .reAmount(reAmount)
                                                .discountPercent(discountPercent)
                                                .validFrom(LocalDate.now())
                                                .validTo(null)
                                                .isFromHistory(false)
                                                .build());
                        }
                }
                return results;
        }
}
