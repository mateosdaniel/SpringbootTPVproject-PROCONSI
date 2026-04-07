package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.BulkPriceMatrixUpdateRequest;
import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.dto.ProductPriceResponse;
import com.proconsi.electrobazar.dto.PriceMatrixSummaryDTO;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.model.TariffPriceHistory;
import com.proconsi.electrobazar.repository.ProductPriceRepository;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductPriceService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proconsi.electrobazar.repository.specification.ProductPriceSpecification;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ProductPriceService}.
 * Manages chronological price history and scheduling for products and tariffs.
 * Integrates with Spring Caching to optimize real-time price lookups.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductPriceServiceImpl implements ProductPriceService {

    private static final String CACHE_NAME = "productPrices";

    private final ProductPriceRepository productPriceRepository;
    private final ProductRepository productRepository;
    private final ActivityLogService activityLogService;
    private final TariffRepository tariffRepository;
    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final RecargoEquivalenciaCalculator recargoCalculator;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#productId", unless = "#result == null")
    public ProductPrice getCurrentPrice(Long productId, LocalDateTime at) {
        return productPriceRepository.findActivePriceAt(productId, at).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductPrice> getFilteredFuturePrices(String search, Pageable pageable) {
        Specification<ProductPrice> spec = ProductPriceSpecification.filterFuturePrices(search);
        return productPriceRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductPrice> getActivePrices(List<Long> productIds, LocalDateTime at) {
        if (productIds == null || productIds.isEmpty()) return new ArrayList<>();
        return productPriceRepository.findActivePricesForProducts(productIds, at);
    }

    @Override
    @CacheEvict(value = CACHE_NAME, key = "#productId")
    public ProductPriceResponse schedulePrice(Long productId, ProductPriceRequest request) {
        if (request.getStartDate() == null) {
            throw new IllegalArgumentException("Price start date is mandatory.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        LocalDateTime newStartDate = request.getStartDate();

        // 1. Close current open price ( endDate is NULL )
        productPriceRepository.findCurrentOpenPrice(productId).ifPresent(current -> {
            current.setEndDate(newStartDate.minusSeconds(1));
            productPriceRepository.save(current);
        });

        // 2. Schedule new price
        BigDecimal vatRate = request.getVatRate() != null ? request.getVatRate() : 
                (product.getTaxRate() != null ? product.getTaxRate().getVatRate() : new BigDecimal("0.21"));

        ProductPrice newPrice = ProductPrice.builder()
                .product(product)
                .vatRate(vatRate)
                .startDate(newStartDate)
                .label(request.getLabel())
                .build();
        newPrice.setPrice(request.getPrice()); // Gross to net conversion happens in setter

        ProductPrice saved = productPriceRepository.save(newPrice);

        // Immediate sync with product table for TPV display
        product.setPrice(request.getPrice());
        productRepository.save(product);

        activityLogService.logActivity("PROGRAMAR_PRECIO", 
                String.format("Nuevo precio programado para '%s': %.2f € a partir de %s", 
                product.getName(), saved.getPrice(), newStartDate), "Admin", "PRODUCT", productId);

        return toResponse(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceResponse> getPriceHistory(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        LocalDateTime now = LocalDateTime.now();
        ProductPrice activePrice = getCurrentPrice(productId, now);

        List<ProductPrice> allPrices = productPriceRepository.findAllByProductId(productId).stream()
                .sorted(Comparator.comparing(ProductPrice::getStartDate))
                .collect(Collectors.toList());

        List<ProductPriceResponse> responses = new ArrayList<>();
        BigDecimal previousPrice = null;

        for (ProductPrice p : allPrices) {
            BigDecimal diff = (previousPrice != null) ? p.getPrice().subtract(previousPrice) : null;
            BigDecimal diffPct = (diff != null) ? diff.divide(previousPrice, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) : null;

            responses.add(toResponse(p, p.equals(activePrice)).toBuilder()
                    .priceChange(diff)
                    .priceChangePct(diffPct)
                    .build());
            previousPrice = p.getPrice();
        }

        responses.sort((a, b) -> a.isCurrentlyActive() ? -1 : (b.isCurrentlyActive() ? 1 : b.getStartDate().compareTo(a.getStartDate())));
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceResponse> getFuturePrices() {
        return productPriceRepository.findAllFuturePrices(LocalDateTime.now()).stream()
                .map(p -> toResponse(p, false))
                .collect(Collectors.toList());
    }

    @Override
    public ProductPriceResponse toResponse(ProductPrice price, boolean isActive) {
        return ProductPriceResponse.builder()
                .id(price.getId())
                .productId(price.getProduct().getId())
                .productName(price.getProduct().getName())
                .price(price.getPrice())
                .vatRate(price.getVatRate())
                .startDate(price.getStartDate())
                .endDate(price.getEndDate())
                .label(price.getLabel())
                .createdAt(price.getCreatedAt())
                .currentlyActive(isActive)
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public List<ProductPriceResponse> bulkSchedulePrice(BulkPriceUpdateRequest request) {
        LocalDateTime effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDateTime.now();
        LocalDateTime closingDate = effectiveDate.minusSeconds(1);

        List<ProductPriceResponse> responses = new ArrayList<>();
        List<Tariff> activeTariffs = tariffRepository.findByActiveTrueOrderByNameAsc();
        List<Long> selectedTariffIds = request.getTariffIds() != null ? request.getTariffIds() : new ArrayList<>();

        for (Long productId : request.getProductIds()) {
            Product product = productRepository.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found."));

            ProductPrice current = productPriceRepository.findCurrentOpenPrice(productId).orElse(null);
            BigDecimal oldGross = (current != null) ? current.getPrice() : product.getPrice();
            BigDecimal vatRate = (request.getVatRate() != null) ? request.getVatRate() : 
                                 ((current != null) ? current.getVatRate() : product.getTaxRate().getVatRate());

            if (current != null) {
                current.setEndDate(closingDate);
                productPriceRepository.save(current);
            }

            BigDecimal newGross = (request.getPercentage() != null) 
                    ? oldGross.multiply(BigDecimal.ONE.add(request.getPercentage().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    : oldGross.add(request.getFixedAmount() != null ? request.getFixedAmount() : BigDecimal.ZERO);
            newGross = newGross.setScale(2, RoundingMode.HALF_UP);

            ProductPrice nextEntry = ProductPrice.builder()
                    .product(product)
                    .vatRate(vatRate)
                    .startDate(effectiveDate)
                    .build();
            nextEntry.setPrice(newGross);
            ProductPrice saved = productPriceRepository.save(nextEntry);

            product.setPrice(newGross);
            productRepository.save(product);

            // Sync with all active tariffs
            for (Tariff t : activeTariffs) {
                tariffPriceHistoryRepository.findCurrentByProductAndTariff(productId, t.getId()).ifPresent(h -> {
                    h.setValidTo(closingDate.toLocalDate());
                    tariffPriceHistoryRepository.save(h);
                });

                BigDecimal oldBase = product.getPrice();
                BigDecimal newBase = selectedTariffIds.contains(t.getId()) ? newGross : oldBase;
                
                // 1. Extraemos el multiplicador de descuento
                BigDecimal discountMult = BigDecimal.ONE.subtract(
                    (t.getDiscountPercentage() != null ? t.getDiscountPercentage() : BigDecimal.ZERO)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                );

                // 2. Extraemos la BASE IMPONIBLE REAL del precio bruto y aplicamos el descuento (Cálculo corregido)
                BigDecimal net = newBase.divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP)
                                        .multiply(discountMult)
                                        .setScale(2, RoundingMode.HALF_UP);

                BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);

                // 3. Generamos el snapshot con los totales correctos
                TariffPriceHistory news = TariffPriceHistory.builder()
                        .product(product).tariff(t).basePrice(newBase).netPrice(net).vatRate(vatRate)
                        .priceWithVat(net.multiply(BigDecimal.ONE.add(vatRate)).setScale(2, RoundingMode.HALF_UP))
                        .reRate(reRate).priceWithRe(net.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(2, RoundingMode.HALF_UP))
                        .discountPercent(t.getDiscountPercentage()).validFrom(effectiveDate.toLocalDate())
                        .build();
                tariffPriceHistoryRepository.save(news);
            }
            responses.add(toResponse(saved, false));
        }

        activityLogService.logActivity("PROGRAMAR_PRECIOS_MASIVOS", 
                "Actualización masiva de precios para " + responses.size() + " productos.", "Admin", "PRODUCT", null);

        return responses;
    }

    @Override
    @Transactional
    public void bulkMatrixUpdate(BulkPriceMatrixUpdateRequest request) {
        LocalDateTime effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDateTime.now();
        LocalDateTime closingDate = effectiveDate.minusSeconds(1);

        for (BulkPriceMatrixUpdateRequest.PriceChangeItem change : request.getChanges()) {
            Product product = productRepository.findById(change.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product " + change.getProductId() + " not found."));

            if (change.getTariffId() == null) {
                // Update BASE price
                productPriceRepository.findCurrentOpenPrice(product.getId()).ifPresent(current -> {
                    current.setEndDate(closingDate);
                    productPriceRepository.save(current);
                });

                ProductPrice nextEntry = ProductPrice.builder()
                        .product(product)
                        .vatRate(product.getTaxRate() != null ? product.getTaxRate().getVatRate() : new BigDecimal("0.21"))
                        .startDate(effectiveDate)
                        .price(change.getNewPrice())
                        .build();
                productPriceRepository.save(nextEntry);

                // Immediate update in product table if effective date is now or past
                if (effectiveDate.isBefore(LocalDateTime.now()) || effectiveDate.isEqual(LocalDateTime.now())) {
                    product.setPrice(change.getNewPrice());
                    productRepository.save(product);
                }
            } else {
                // Update TARIFF specific price
                Tariff tariff = tariffRepository.findById(change.getTariffId())
                        .orElseThrow(() -> new ResourceNotFoundException("Tariff " + change.getTariffId() + " not found."));

                tariffPriceHistoryRepository.findCurrentByProductAndTariff(product.getId(), tariff.getId()).ifPresent(current -> {
                    current.setValidTo(closingDate.toLocalDate());
                    tariffPriceHistoryRepository.save(current);
                });

                BigDecimal vatRate = product.getTaxRate() != null ? product.getTaxRate().getVatRate() : new BigDecimal("0.21");
                BigDecimal net = change.getNewPrice().divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
                BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);

                TariffPriceHistory history = TariffPriceHistory.builder()
                        .product(product)
                        .tariff(tariff)
                        .basePrice(product.getPrice())
                        .netPrice(net)
                        .vatRate(vatRate)
                        .priceWithVat(change.getNewPrice())
                        .reRate(reRate)
                        .priceWithRe(net.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(2, RoundingMode.HALF_UP))
                        .discountPercent(BigDecimal.ZERO) // Using zero because it's now a manual override
                        .validFrom(effectiveDate.toLocalDate())
                        .build();
                tariffPriceHistoryRepository.save(history);
            }
        }
        
        activityLogService.logActivity("MATRIZ_PRECIOS_CAMBIO", 
                "Actualización de matriz de precios procesada para " + request.getChanges().size() + " entradas.", "Admin", "PRICE", null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceMatrixSummaryDTO> getPendingMatrixUpdates() {
        LocalDateTime now = LocalDateTime.now();
        List<PriceMatrixSummaryDTO> results = new ArrayList<>();

        // Pending Base price changes
        productPriceRepository.findAllFuturePrices(now).forEach(p -> {
            BigDecimal oldPrice = productPriceRepository.findActivePriceAt(p.getProduct().getId(), p.getStartDate().minusSeconds(5))
                    .map(ProductPrice::getPrice).orElse(BigDecimal.ZERO);
            results.add(PriceMatrixSummaryDTO.builder()
                    .id(p.getId())
                    .productId(p.getProduct().getId())
                    .productName(p.getProduct().getName())
                    .tariffName("Retail (Base)")
                    .oldPrice(oldPrice)
                    .newPrice(p.getPrice())
                    .startDate(p.getStartDate())
                    .pending(true)
                    .build());
        });

        // Pending Tariff changes (simplified: we use validFrom > today)
        tariffPriceHistoryRepository.findAll().stream()
                .filter(h -> h.getValidFrom().isAfter(LocalDate.now()))
                .forEach(h -> {
                    BigDecimal oldPrice = tariffPriceHistoryRepository.findByProductIdOrderByValidFromDesc(h.getProduct().getId()).stream()
                            .filter(prev -> prev.getTariff().getId().equals(h.getTariff().getId()) && prev.getValidFrom().isBefore(h.getValidFrom()))
                            .findFirst().map(TariffPriceHistory::getPriceWithVat).orElse(BigDecimal.ZERO);
                    results.add(PriceMatrixSummaryDTO.builder()
                            .id(h.getId())
                            .productId(h.getProduct().getId())
                            .productName(h.getProduct().getName())
                            .tariffId(h.getTariff().getId())
                            .tariffName(h.getTariff().getName())
                            .oldPrice(oldPrice)
                            .newPrice(h.getPriceWithVat())
                            .startDate(h.getValidFrom().atStartOfDay())
                            .pending(true)
                            .build());
                });

        results.sort(Comparator.comparing(PriceMatrixSummaryDTO::getStartDate));
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceMatrixSummaryDTO> getMatrixUpdateHistory() {
        // Just return the last 50 activities or history entries
        List<PriceMatrixSummaryDTO> results = new ArrayList<>();
        
        // From Tariff History (last applied)
        tariffPriceHistoryRepository.findAll().stream()
                .filter(h -> h.getValidFrom().isBefore(LocalDate.now()) || h.getValidFrom().isEqual(LocalDate.now()))
                .sorted(Comparator.comparing(TariffPriceHistory::getCreatedAt).reversed())
                .limit(30)
                .forEach(h -> {
                    BigDecimal oldPrice = tariffPriceHistoryRepository.findByProductIdOrderByValidFromDesc(h.getProduct().getId()).stream()
                            .filter(prev -> prev.getTariff().getId().equals(h.getTariff().getId()) && prev.getValidFrom().isBefore(h.getValidFrom()))
                            .findFirst().map(TariffPriceHistory::getPriceWithVat).orElse(BigDecimal.ZERO);
                    results.add(PriceMatrixSummaryDTO.builder()
                            .productId(h.getProduct().getId())
                            .productName(h.getProduct().getName())
                            .tariffName(h.getTariff().getName())
                            .oldPrice(oldPrice)
                            .newPrice(h.getPriceWithVat())
                            .createdAt(h.getCreatedAt())
                            .pending(false)
                            .build());
                });
                
        return results;
    }

    @Override
    @Transactional
    public void deletePendingPrice(Long id) {
        // Try deleting from ProductPrice first
        if (productPriceRepository.existsById(id)) {
            productPriceRepository.findById(id).ifPresent(p -> {
                if (p.getStartDate().isAfter(LocalDateTime.now())) {
                    productPriceRepository.delete(p);
                }
            });
        } 
        // Then try TariffPriceHistory
        else if (tariffPriceHistoryRepository.existsById(id)) {
            tariffPriceHistoryRepository.findById(id).ifPresent(h -> {
                if (h.getValidFrom().isAfter(LocalDate.now())) {
                    tariffPriceHistoryRepository.delete(h);
                }
            });
        }
    }
}