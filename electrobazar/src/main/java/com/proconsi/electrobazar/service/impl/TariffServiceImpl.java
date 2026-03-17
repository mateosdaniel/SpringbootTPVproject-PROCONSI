package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.model.TariffPriceHistory;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.TariffService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Implementation of {@link TariffService}.
 * Orchestrates tariff management and ensures that price history across all tariffs 
 * remains in sync whenever product prices or VAT rates are updated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TariffServiceImpl implements TariffService {

    private final TariffRepository tariffRepository;
    private final CustomerRepository customerRepository;
    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public List<Tariff> findAll() {
        return tariffRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tariff> findAllActive() {
        return tariffRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tariff> findById(Long id) {
        return tariffRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tariff> findByName(String name) {
        return tariffRepository.findByName(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Tariff getDefault() {
        return tariffRepository.findByName(Tariff.MINORISTA)
                .orElseThrow(() -> new IllegalStateException("Critical: Default tariff MINORISTA missing."));
    }

    @Override
    public Tariff create(String name, BigDecimal discount, String description) {
        String upperName = name.toUpperCase();
        if (tariffRepository.findByName(upperName).isPresent()) {
            throw new IllegalArgumentException("A tariff named " + upperName + " already exists.");
        }
        Tariff tariff = Tariff.builder()
                .name(upperName).discountPercentage(discount != null ? discount : BigDecimal.ZERO)
                .description(description).active(true).systemTariff(false).build();
        Tariff saved = tariffRepository.save(tariff);
        activityLogService.logActivity("CREAR_TARIFA", String.format("Tariff created: %s (Discount: %.2f%%)", upperName, saved.getDiscountPercentage()), "Admin", "TARIFF", saved.getId());
        return saved;
    }

    @Override
    public Tariff update(Long id, BigDecimal discount, String description) {
        Tariff tariff = tariffRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tariff #" + id + " not found."));
        tariff.setDiscountPercentage(discount != null ? discount : BigDecimal.ZERO);
        tariff.setDescription(description);
        Tariff saved = tariffRepository.save(tariff);
        activityLogService.logActivity("ACTUALIZAR_TARIFA", String.format("Tariff updated: %s (New Discount: %.2f%%)", saved.getName(), saved.getDiscountPercentage()), "Admin", "TARIFF", saved.getId());
        return saved;
    }

    @Override
    public void deactivate(Long id) {
        Tariff tariff = tariffRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tariff #" + id + " not found."));
        if (Boolean.TRUE.equals(tariff.getSystemTariff())) {
            throw new IllegalStateException("Cannot deactivate system tariff: " + tariff.getName());
        }
        tariff.setActive(false);
        tariffRepository.save(tariff);

        // Safety: Reassign customers to default MINORISTA tariff
        Tariff defaultTariff = getDefault();
        List<Customer> affected = customerRepository.findAll().stream().filter(c -> tariff.equals(c.getTariff())).toList();
        affected.forEach(c -> c.setTariff(defaultTariff));
        customerRepository.saveAll(affected);

        activityLogService.logActivity("DESACTIVAR_TARIFA", "Tariff deactivated: " + tariff.getName(), "Admin", "TARIFF", tariff.getId());
    }

    @Override
    public void activate(Long id) {
        tariffRepository.findById(id).ifPresent(t -> {
            t.setActive(true);
            tariffRepository.save(t);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getCustomerCountPerTariff() {
        Map<Long, Long> stats = new HashMap<>();
        tariffRepository.countCustomersPerTariff().forEach(row -> stats.put((Long) row[0], (Long) row[1]));
        return stats;
    }

    @Override
    public void regenerateTariffHistoryForProducts(List<Product> affectedProducts) {
        if (affectedProducts == null || affectedProducts.isEmpty()) return;
        List<Tariff> activeTariffs = findAllActive();
        if (activeTariffs.isEmpty()) return;

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // 1. Close current histories
        List<TariffPriceHistory> toClose = new ArrayList<>();
        for (Product product : affectedProducts) {
            for (Tariff tariff : activeTariffs) {
                tariffPriceHistoryRepository.findCurrentByProductAndTariff(product.getId(), tariff.getId())
                        .ifPresent(h -> {
                            h.setValidTo(yesterday);
                            toClose.add(h);
                        });
            }
        }
        if (!toClose.isEmpty()) tariffPriceHistoryRepository.saveAll(toClose);

        // 2. Open new histories with current VAT and product prices
        List<TariffPriceHistory> newRecords = new ArrayList<>();
        for (Product product : affectedProducts) {
            if (product.getPrice() == null) continue;

            BigDecimal vatRate = (product.getTaxRate() != null) ? product.getTaxRate().getVatRate() : new BigDecimal("0.21");
            BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);
            BigDecimal baseGross = product.getPrice();

            for (Tariff tariff : activeTariffs) {
                BigDecimal discountMult = BigDecimal.ONE.subtract((tariff.getDiscountPercentage() != null ? tariff.getDiscountPercentage() : BigDecimal.ZERO).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
                
                BigDecimal net = baseGross.divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP).multiply(discountMult).setScale(2, RoundingMode.HALF_UP);
                BigDecimal withVat = net.multiply(BigDecimal.ONE.add(vatRate)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal withRecargo = net.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(2, RoundingMode.HALF_UP);

                newRecords.add(TariffPriceHistory.builder()
                        .product(product).tariff(tariff).basePrice(baseGross).netPrice(net).vatRate(vatRate)
                        .priceWithVat(withVat).reRate(reRate).priceWithRe(withRecargo)
                        .discountPercent(tariff.getDiscountPercentage()).validFrom(today).build());
            }
        }
        if (!newRecords.isEmpty()) {
            tariffPriceHistoryRepository.saveAll(newRecords);
            activityLogService.logActivity("REGENERAR_PRECIOS_TARIFA", 
                String.format("Prices regenerated for %d products across %d active tariffs.", affectedProducts.size(), activeTariffs.size()), "System", "TARIFF", null);
        }
    }
}


