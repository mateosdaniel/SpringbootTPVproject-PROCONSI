package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.model.TariffPriceHistory;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TariffServiceImpl implements TariffService {

    private final TariffRepository tariffRepository;
    private final CustomerRepository customerRepository;
    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final RecargoEquivalenciaCalculator recargoCalculator;

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
                .orElseThrow(() -> new IllegalStateException(
                        "System tariff MINORISTA not found \u2013 data initializer may have failed."));
    }

    @Override
    public Tariff create(String name, BigDecimal discountPercentage, String description) {
        if (tariffRepository.findByName(name.toUpperCase()).isPresent()) {
            throw new IllegalArgumentException("Ya existe una tarifa con el nombre: " + name);
        }
        Tariff tariff = Tariff.builder()
                .name(name.toUpperCase())
                .discountPercentage(discountPercentage != null ? discountPercentage : BigDecimal.ZERO)
                .description(description)
                .active(true)
                .systemTariff(false)
                .build();
        return tariffRepository.save(tariff);
    }

    @Override
    public Tariff update(Long id, BigDecimal discountPercentage, String description) {
        Tariff tariff = tariffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tarifa no encontrada con id: " + id));
        tariff.setDiscountPercentage(discountPercentage != null ? discountPercentage : BigDecimal.ZERO);
        tariff.setDescription(description);
        return tariffRepository.save(tariff);
    }

    @Override
    public void deactivate(Long id) {
        Tariff tariff = tariffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tarifa no encontrada con id: " + id));
        if (Boolean.TRUE.equals(tariff.getSystemTariff())) {
            throw new IllegalStateException("No se puede desactivar una tarifa del sistema: " + tariff.getName());
        }
        tariff.setActive(false);
        tariffRepository.save(tariff);

        // Move all customers using this tariff back to MINORISTA
        Tariff minorista = getDefault();
        List<Customer> affected = customerRepository.findAll().stream()
                .filter(c -> tariff.equals(c.getTariff()))
                .toList();
        affected.forEach(c -> c.setTariff(minorista));
        customerRepository.saveAll(affected);
        log.info("Tariff '{}' deactivated. {} customers moved to MINORISTA.", tariff.getName(), affected.size());
    }

    @Override
    public void activate(Long id) {
        Tariff tariff = tariffRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tarifa no encontrada con id: " + id));
        tariff.setActive(true);
        tariffRepository.save(tariff);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getCustomerCountPerTariff() {
        Map<Long, Long> result = new HashMap<>();
        tariffRepository.countCustomersPerTariff()
                .forEach(row -> result.put((Long) row[0], (Long) row[1]));
        return result;
    }

    /**
     * Closes any open tariff_price_history records for the affected products and
     * creates fresh records for every active tariff using the new VAT rate already
     * stored in {@code product.getTaxRate()}.
     *
     * <p>Algorithm (per product x tariff pair):
     * <ol>
     *   <li>Close existing open record: set {@code valid_to = today - 1 day}</li>
     *   <li>Compute from {@code product.price} (gross price inclusive of VAT):
     *       <pre>
     *       net_price      = (product.price / (1 + newVatRate)) * (1 - discount% / 100)
     *       price_with_vat = net_price * (1 + newVatRate)
     *       price_with_re  = net_price * (1 + newVatRate + reRate)
     *       </pre>
     *   </li>
     *   <li>Bulk-insert all new records via {@code saveAll()}</li>
     * </ol>
     * </p>
     *
     * @param affectedProducts products whose VAT rate has just been updated
     */
    @Override
    public void regenerateTariffHistoryForProducts(List<Product> affectedProducts) {
        if (affectedProducts == null || affectedProducts.isEmpty()) {
            return;
        }

        List<Tariff> activeTariffs = tariffRepository.findByActiveTrueOrderByNameAsc();
        if (activeTariffs.isEmpty()) {
            return;
        }

        LocalDate today     = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // ── 1. Close all currently open records for every affected product x tariff ──
        List<TariffPriceHistory> toClose = new ArrayList<>();
        for (Product product : affectedProducts) {
            for (Tariff tariff : activeTariffs) {
                tariffPriceHistoryRepository
                        .findCurrentByProductAndTariff(product.getId(), tariff.getId())
                        .ifPresent(h -> {
                            h.setValidTo(yesterday);
                            toClose.add(h);
                        });
            }
        }
        if (!toClose.isEmpty()) {
            tariffPriceHistoryRepository.saveAll(toClose);
        }

        // ── 2. Build new records for every product x tariff ──────────────────────────
        List<TariffPriceHistory> newRecords = new ArrayList<>();

        for (Product product : affectedProducts) {
            if (product.getPrice() == null) continue;

            // New VAT rate is already persisted on the product entity at this point
            BigDecimal newVatRate = (product.getTaxRate() != null && product.getTaxRate().getVatRate() != null)
                    ? product.getTaxRate().getVatRate()
                    : new BigDecimal("0.21");

            BigDecimal reRate     = recargoCalculator.getRecargoRate(newVatRate);
            BigDecimal grossPrice = product.getPrice(); // product.price = gross (VAT-inclusive) base

            for (Tariff tariff : activeTariffs) {
                BigDecimal discountPct = tariff.getDiscountPercentage() != null
                        ? tariff.getDiscountPercentage()
                        : BigDecimal.ZERO;

                // net_price = (product.price / (1 + vatRate)) x (1 - discount% / 100)
                BigDecimal discountMultiplier = BigDecimal.ONE
                        .subtract(discountPct.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
                BigDecimal netPrice = grossPrice
                        .divide(BigDecimal.ONE.add(newVatRate), 10, RoundingMode.HALF_UP)
                        .multiply(discountMultiplier)
                        .setScale(2, RoundingMode.HALF_UP);

                BigDecimal priceWithVat = netPrice
                        .multiply(BigDecimal.ONE.add(newVatRate))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal priceWithRe  = netPrice
                        .multiply(BigDecimal.ONE.add(newVatRate).add(reRate))
                        .setScale(2, RoundingMode.HALF_UP);

                newRecords.add(TariffPriceHistory.builder()
                        .product(product)
                        .tariff(tariff)
                        .basePrice(grossPrice)
                        .netPrice(netPrice)
                        .vatRate(newVatRate)
                        .priceWithVat(priceWithVat)
                        .reRate(reRate)
                        .priceWithRe(priceWithRe)
                        .discountPercent(discountPct.setScale(2, RoundingMode.HALF_UP))
                        .validFrom(today)
                        .validTo(null)
                        .build());
            }
        }

        if (!newRecords.isEmpty()) {
            tariffPriceHistoryRepository.saveAll(newRecords);
            log.info("Regenerated {} tariff_price_history records for {} products x {} tariffs.",
                    newRecords.size(), affectedProducts.size(), activeTariffs.size());
        }
    }
}
