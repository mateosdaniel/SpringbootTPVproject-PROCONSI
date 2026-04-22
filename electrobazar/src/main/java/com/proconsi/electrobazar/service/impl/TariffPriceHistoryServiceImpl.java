package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.model.TariffPriceHistory;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.TariffPriceHistoryService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TariffPriceHistoryServiceImpl implements TariffPriceHistoryService {

    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final ProductRepository productRepository;
    private final TariffRepository tariffRepository;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final EntityManager entityManager; // <-- AÑADIDO: Necesario para liberar memoria

    private static final Set<Long> activeInitializations = ConcurrentHashMap.newKeySet();

    @Override
    public List<TariffPriceHistory> getHistoryByTariff(Long tariffId) {
        return tariffPriceHistoryRepository.findByTariffIdOrderByValidFromDesc(tariffId);
    }

    @Override
    public List<TariffPriceHistory> getHistoryByProduct(Long productId) {
        return tariffPriceHistoryRepository.findByProductIdOrderByValidFromDesc(productId);
    }

    @Override
    public List<LocalDate> getDistinctValidFromDates(Long tariffId) {
        return tariffPriceHistoryRepository.findDistinctValidFromByTariffId(tariffId).stream()
                .map(sqlDate -> sqlDate.toLocalDate())
                .collect(Collectors.toList());
    }

    @Override
    public List<LocalTime> getVersionsForDate(Long tariffId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return tariffPriceHistoryRepository.findVersionsForTariffAndDayRange(tariffId, start, end).stream()
                .map(LocalDateTime::toLocalTime)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Page<TariffPriceEntryDTO> getCurrentPricesForTariff(Long tariffId, Pageable pageable) {
        // Just return current data snapshot (use LocalDateTime.now() for approximation
        // or latest specific)
        return getPricesForTariffAtExactDateTime(tariffId, LocalDate.now(), LocalTime.now(), pageable);
    }

    @Override
    public Page<TariffPriceEntryDTO> getPricesForTariffAtExactDateTime(Long tariffId, LocalDate date, LocalTime time,
            Pageable pageable) {
        LocalDateTime targetDateTime = (time != null) ? date.atTime(time) : date.atTime(LocalTime.MAX);

        // Buscamos los registros que estaban ACTIVOS en ese instante (dentro de un rango [validFrom, validTo])
        Page<TariffPriceHistory> historyPage = tariffPriceHistoryRepository.findByTariffIdAndDateTime(tariffId,
                targetDateTime, pageable);

        return historyPage.map(this::mapToDTO);
    }

    @Override
    public Page<TariffPriceEntryDTO> getPricesForTariffAtExactValidFrom(Long tariffId, LocalDate date, LocalTime time,
            Pageable pageable) {
        LocalDateTime targetDateTime = (time != null) ? date.atTime(time) : date.atTime(LocalTime.MIN);

        // Buscamos los registros que EMPEZARON exactamente a esa hora (la "versión")
        Page<TariffPriceHistory> historyPage = tariffPriceHistoryRepository.findByTariffIdAndValidFrom(tariffId,
                targetDateTime, pageable);

        return historyPage.map(this::mapToDTO);
    }

    @Override
    public List<TariffPriceEntryDTO> getPricesForTariffAtExactDateTimeList(Long tariffId, LocalDate date,
            LocalTime time) {
        LocalDateTime targetDateTime = (time != null) ? date.atTime(time) : date.atTime(LocalTime.MAX);

        // Usamos la consulta Robusta para el PDF: devuelve el último precio conocido de CADA producto
        List<TariffPriceHistory> histories = tariffPriceHistoryRepository.findAllLatestByTariffIdAndDateTime(tariffId,
                targetDateTime);

        return histories.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private TariffPriceEntryDTO mapToDTO(TariffPriceHistory h) {
        return TariffPriceEntryDTO.builder()
                .productId(h.getProduct().getId()).productName(h.getProduct().getName())
                .categoryName(h.getProduct().getCategory() != null ? h.getProduct().getCategory().getName()
                        : "Uncategorized")
                .basePrice(h.getBasePrice()).netPrice(h.getNetPrice()).vatRate(h.getVatRate())
                .priceWithVat(h.getPriceWithVat())
                .reRate(h.getReRate())
                .priceWithRe(h.getPriceWithRe() != null ? h.getPriceWithRe() : h.getNetPrice())
                .vatAmount(h.getPriceWithVat().subtract(h.getNetPrice()))
                .reAmount(h.getPriceWithRe() != null ? h.getPriceWithRe().subtract(h.getPriceWithVat())
                        : BigDecimal.ZERO)
                .discountPercent(h.getDiscountPercent())
                .validFrom(h.getValidFrom().toLocalDate())
                .validTo(h.getValidTo() != null ? h.getValidTo().toLocalDate() : null)
                .isFromHistory(true).build();
    }

    @Override
    public boolean isInitializationInProgress(Long tariffId) {
        return activeInitializations.contains(tariffId);
    }

    @Override
    @Async
    @Transactional
    public void generateInitialSnapshotIfEmpty(Long tariffId) {
        if (tariffPriceHistoryRepository.existsByTariffId(tariffId))
            return;

        if (!activeInitializations.add(tariffId)) {
            log.info("Inicialización de historial para tarifa {} ya está en progreso por otro hilo.", tariffId);
            return;
        }

        try {
            if (tariffPriceHistoryRepository.existsByTariffId(tariffId))
                return;

            Tariff tariff = tariffRepository.findById(tariffId).orElse(null);
            if (tariff == null || tariff.getActive() == null || !tariff.getActive())
                return;

            log.info("Iniciando generación de snapshot inicial para tarifa: {}", tariff.getName());

            // Usamos el método optimizado para evitar consultas N+1
            List<Product> products = productRepository.findAllActiveForSnapshot();
            Map<BigDecimal, BigDecimal> reRateMap = recargoCalculator.getVatToReRateMap();

            List<TariffPriceHistory> snapshots = new ArrayList<>();
            BigDecimal discountPercent = tariff.getDiscountPercentage() != null ? tariff.getDiscountPercentage()
                    : BigDecimal.ZERO;
            BigDecimal discountMult = BigDecimal.ONE.subtract(
                    discountPercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));

            LocalDateTime snapshotTime = LocalDateTime.now();

            for (Product product : products) {
                BigDecimal grossPrice = product.getPrice();
                BigDecimal vatRate = product.getTaxRate() != null ? product.getTaxRate().getVatRate() : BigDecimal.ZERO;

                BigDecimal net = grossPrice
                        .divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP)
                        .multiply(discountMult)
                        .setScale(4, RoundingMode.HALF_UP);

                BigDecimal reRate = BigDecimal.ZERO;
                BigDecimal normalizedVat = vatRate.stripTrailingZeros();
                for (Map.Entry<BigDecimal, BigDecimal> entry : reRateMap.entrySet()) {
                    if (entry.getKey().stripTrailingZeros().compareTo(normalizedVat) == 0) {
                        reRate = entry.getValue();
                        break;
                    }
                }

                snapshots.add(TariffPriceHistory.builder()
                        .product(product)
                        .tariff(tariff)
                        .basePrice(grossPrice)
                        .netPrice(net)
                        .vatRate(vatRate)
                        .priceWithVat(net.multiply(BigDecimal.ONE.add(vatRate)).setScale(4, RoundingMode.HALF_UP))
                        .reRate(reRate)
                        .priceWithRe(
                                net.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(4, RoundingMode.HALF_UP))
                        .discountPercent(discountPercent)
                        .validFrom(snapshotTime)
                        .createdAt(snapshotTime)
                        .build());

                // Lotes de 1000 para máximo rendimiento en MySQL
                if (snapshots.size() >= 1000) {
                    tariffPriceHistoryRepository.saveAll(snapshots);
                    tariffPriceHistoryRepository.flush(); // Fuerza la escritura a BD
                    entityManager.clear(); // ¡MAGIA! Libera la RAM para que el proceso no se ahogue
                    snapshots.clear();
                }
            }

            if (!snapshots.isEmpty()) {
                tariffPriceHistoryRepository.saveAll(snapshots);
                tariffPriceHistoryRepository.flush();
                entityManager.clear();
            }

            // --- SINCRONIZACIÓN DE VERSIÓN ---
            // El proceso puede tardar varios segundos/minutos. Forzamos que todos los
            // registros
            // compartan exactamente el mismo segundo de finalización para agruparlos como
            // una única versión.
            LocalDateTime endTime = LocalDateTime.now();
            tariffPriceHistoryRepository.updateValidFromForTariffAndTime(tariffId, snapshotTime, endTime);
            // ----------------------------------

            log.info("Finalizada generación de snapshot inicial para tarifa {}. Productos procesados: {}",
                    tariff.getName(), products.size());

        } finally {
            activeInitializations.remove(tariffId);
        }
    }
}