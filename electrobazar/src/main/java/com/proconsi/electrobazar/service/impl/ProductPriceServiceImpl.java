package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.dto.ProductPriceResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        log.debug("Cache miss for current price of product ID: {}", productId);
        return productPriceRepository.findActivePriceAt(productId, at).orElse(null);
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
                String.format("New price scheduled for '%s': %.2f € starting %s", 
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

                BigDecimal oldBase = product.getPrice(); // Simplified logic for bulk update
                BigDecimal newBase = selectedTariffIds.contains(t.getId()) ? newGross : oldBase;
                
                BigDecimal discountMult = BigDecimal.ONE.subtract((t.getDiscountPercentage() != null ? t.getDiscountPercentage() : BigDecimal.ZERO).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                BigDecimal net = newBase.multiply(discountMult).setScale(2, RoundingMode.HALF_UP);
                BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);

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
                "Bulk price update for " + responses.size() + " products.", "Admin", "PRODUCT", null);

        return responses;
    }
}