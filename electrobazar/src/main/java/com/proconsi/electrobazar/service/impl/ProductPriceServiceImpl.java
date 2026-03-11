package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.BulkPriceUpdateRequest;
import com.proconsi.electrobazar.dto.ProductPriceRequest;
import com.proconsi.electrobazar.dto.ProductPriceResponse;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.repository.ProductPriceRepository;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductPriceService;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ProductPriceService} providing temporal price
 * management
 * with caching support.
 *
 * <h3>Caching Strategy</h3>
 * <p>
 * The {@code getCurrentPrice} method is cached under the key
 * {@code "productPrices::{productId}"}. The cache is evicted whenever a new
 * price
 * is scheduled for that product via {@code schedulePrice}.
 * </p>
 *
 * <h3>Price Scheduling Logic</h3>
 * <p>
 * When a new price is scheduled:
 * </p>
 * <ol>
 * <li>The existing open-ended price (endDate IS NULL) is found.</li>
 * <li>Its endDate is set to {@code newStartDate.minusSeconds(1)} (i.e., one
 * second
 * before the new price takes effect).</li>
 * <li>The new price is saved with the provided startDate and a null
 * endDate.</li>
 * </ol>
 *
 * <p>
 * Example for a Jan 1st price change:
 * </p>
 * 
 * <pre>
 *   Current price: €10.00, startDate=2025-01-01T00:00:00, endDate=null
 *   New price:     €12.00, startDate=2026-01-01T00:00:00
 *
 *   After scheduling:
 *   Current price: €10.00, startDate=2025-01-01T00:00:00, endDate=2025-12-31T23:59:59
 *   New price:     €12.00, startDate=2026-01-01T00:00:00, endDate=null
 * </pre>
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
    private final com.proconsi.electrobazar.repository.TariffRepository tariffRepository;
    private final com.proconsi.electrobazar.repository.TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator recargoCalculator;

    /**
     * {@inheritDoc}
     *
     * <p>
     * Uses Spring Cache with the key {@code #productId} under the cache named
     * {@code "productPrices"}. The {@code at} parameter is intentionally excluded
     * from the cache key to keep the cache simple — it is assumed callers pass
     * {@code LocalDateTime.now()} for real-time lookups. For historical queries,
     * call the repository directly.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "#productId", unless = "#result == null")
    public ProductPrice getCurrentPrice(Long productId, LocalDateTime at) {
        log.debug("Cache miss for productId={} at {}", productId, at);
        return productPriceRepository.findActivePriceAt(productId, at).orElse(null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Evicts the cache entry for the product after scheduling the new price,
     * ensuring the next call to {@code getCurrentPrice} fetches fresh data.
     * </p>
     */
    @Override
    @CacheEvict(value = CACHE_NAME, key = "#productId")
    public ProductPriceResponse schedulePrice(Long productId, ProductPriceRequest request) {
        if (request.getStartDate() == null) {
            throw new IllegalArgumentException("La fecha de inicio (startDate) es obligatoria.");
        }

        // Validate the product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Producto no encontrado con id: " + productId));

        LocalDateTime newStartDate = request.getStartDate();

        // ── Step 1: Close the current open-ended price ─────────────────────────
        // Find the price with endDate IS NULL (the currently active open-ended price)
        productPriceRepository.findCurrentOpenPrice(productId).ifPresent(currentPrice -> {
            // Set its endDate to one second before the new price starts
            // Example: new price starts 2026-01-01T00:00:00 → current ends
            // 2025-12-31T23:59:59
            LocalDateTime closingDate = newStartDate.minusSeconds(1);
            currentPrice.setEndDate(closingDate);
            productPriceRepository.save(currentPrice);
            log.info("Closed existing price (id={}) for product '{}' (id={}). EndDate set to {}",
                    currentPrice.getId(), product.getName(), productId, closingDate);
        });

        // ── Step 2: Create and persist the new scheduled price ─────────────────
        BigDecimal vatRate = request.getVatRate() != null
                ? request.getVatRate()
                : (product.getTaxRate() != null && product.getTaxRate().getVatRate() != null ? product.getTaxRate().getVatRate() : new BigDecimal("0.21")); // Default to Spanish
                                                                                                  // standard VAT rate

        ProductPrice newPrice = ProductPrice.builder()
                .product(product)
                .vatRate(vatRate)
                .startDate(newStartDate)
                .endDate(null) // Open-ended: no scheduled expiry
                .label(request.getLabel())
                .build();

        // Use setPrice to handle the Gross -> Net conversion automatically
        newPrice.setPrice(request.getPrice());

        ProductPrice saved = productPriceRepository.save(newPrice);

        // Also update the base product price so TPV shows the correct price immediately
        product.setPrice(request.getPrice());
        productRepository.save(product);

        log.info("Scheduled new price for product '{}' (id={}): {} € (VAT {}%) starting {}",
                product.getName(), productId,
                saved.getPrice(), vatRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString(),
                newStartDate);

        activityLogService.logActivity(
                "PROGRAMAR_PRECIO",
                "Precio programado para '" + product.getName() + "': "
                        + saved.getPrice().setScale(2, RoundingMode.HALF_UP) + " \u20ac a partir de "
                        + newStartDate.toString().replace("T", " "),
                "Admin",
                "PRODUCT",
                productId);

        return toResponse(saved, false); // Not yet active if startDate is in the future
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceResponse> getPriceHistory(Long productId) {
        // Validate product exists
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Producto no encontrado con id: " + productId);
        }

        LocalDateTime now = LocalDateTime.now();
        ProductPrice activePrice = productPriceRepository.findActivePriceAt(productId, now).orElse(null);

        // Get all prices sorted by startDate ascending (oldest first) for variation
        // calculation
        List<ProductPrice> allPrices = productPriceRepository.findAllByProductId(productId).stream()
                .sorted(Comparator.comparing(ProductPrice::getStartDate))
                .collect(Collectors.toList());

        // Calculate price changes (comparing chronologically)
        List<ProductPriceResponse> responses = new ArrayList<>();
        BigDecimal previousPrice = null;

        for (ProductPrice p : allPrices) {
            BigDecimal priceChange = null;
            BigDecimal priceChangePct = null;

            if (previousPrice != null) {
                priceChange = p.getPrice().subtract(previousPrice);
                // Calculate percentage: (change / previous) * 100
                priceChangePct = priceChange
                        .divide(previousPrice, 6, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }

            ProductPriceResponse response = toResponse(p, p.equals(activePrice)).toBuilder()
                    .priceChange(priceChange)
                    .priceChangePct(priceChangePct)
                    .build();

            responses.add(response);
            previousPrice = p.getPrice();
        }

        // Sort for display: active first, then by startDate descending (newest first)
        responses.sort((a, b) -> {
            if (a.isCurrentlyActive() && !b.isCurrentlyActive())
                return -1;
            if (!a.isCurrentlyActive() && b.isCurrentlyActive())
                return 1;
            // Both active or both not active - sort by startDate descending
            return b.getStartDate().compareTo(a.getStartDate());
        });

        return responses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductPriceResponse> getFuturePrices() {
        LocalDateTime now = LocalDateTime.now();
        return productPriceRepository.findAllFuturePrices(now).stream()
                .map(p -> toResponse(p, false))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>
     * Bulk schedules price updates for multiple products atomically.
     * </p>
     */
    @Override
    @Transactional
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public List<ProductPriceResponse> bulkSchedulePrice(BulkPriceUpdateRequest request) {
        List<Long> productIds = request.getProductIds();
        LocalDateTime effectiveDate = request.getEffectiveDate();

        // Safety net: if no effective date provided, use current time (immediate
        // application)
        if (effectiveDate == null) {
            effectiveDate = LocalDateTime.now();
        }

        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("La lista de IDs de productos no puede estar vacía.");
        }

        if (request.getPercentage() == null && request.getFixedAmount() == null) {
            throw new IllegalArgumentException("Debe especificar either percentage or fixedAmount.");
        }

        if (request.getPercentage() != null && request.getFixedAmount() != null) {
            throw new IllegalArgumentException(
                    "No se puede especificar both percentage and fixedAmount. Use solo uno.");
        }

        // Calculate closing date (one second before effective date)
        LocalDateTime closingDate = effectiveDate.minusSeconds(1);

        List<ProductPriceResponse> results = new ArrayList<>();
        Set<Long> affectedProductIds = productIds.stream().collect(Collectors.toSet());

        List<com.proconsi.electrobazar.model.Tariff> allActiveTariffs = tariffRepository.findByActiveTrueOrderByNameAsc();
        List<Long> selectedTariffIds = request.getTariffIds() != null ? request.getTariffIds() : new ArrayList<>();

        // Process each product
        for (Long productId : productIds) {
            // Find product
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado con id: " + productId));

                // Find current open price
                ProductPrice currentPrice = productPriceRepository.findCurrentOpenPrice(productId)
                        .orElse(null);

                BigDecimal basePrice;
                BigDecimal currentVat;

                if (currentPrice != null) {
                    basePrice = currentPrice.getPrice();
                    currentVat = currentPrice.getVatRate();

                    // Close current price
                    currentPrice.setEndDate(closingDate);
                    productPriceRepository.save(currentPrice);
                    log.info("Closed existing price (id={}) for product '{}' (id={}). EndDate set to {}",
                            currentPrice.getId(), product.getName(), productId, closingDate);
                } else {
                    // Use product's base price if no price history exists
                    basePrice = product.getPrice();
                    currentVat = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null ? product.getTaxRate().getVatRate() : new BigDecimal("0.21");
                }

                // Calculate new price
                BigDecimal newPrice;
                if (request.getPercentage() != null) {
                    // Percentage increase: newPrice = basePrice * (1 + percentage/100)
                    BigDecimal multiplier = BigDecimal.ONE.add(request.getPercentage()
                            .divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP));
                    newPrice = basePrice.multiply(multiplier)
                            .setScale(2, RoundingMode.HALF_UP);
                } else {
                    // Fixed amount increase
                    newPrice = basePrice.add(request.getFixedAmount())
                            .setScale(2, RoundingMode.HALF_UP);
                }

                // Determine VAT rate (use existing or new if provided)
                BigDecimal vatRate = request.getVatRate() != null ? request.getVatRate() : currentVat;

                // Calculate base_price_net: newPrice / (1 + vatRate)
                BigDecimal basePriceNet = newPrice.divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);

                // Create new scheduled price
                ProductPrice newPriceEntity = ProductPrice.builder()
                        .product(product)
                        .vatRate(vatRate)
                        .startDate(effectiveDate)
                        .endDate(null)
                        .label(request.getLabel())
                        .basePriceNet(basePriceNet)
                        .price(basePriceNet.multiply(BigDecimal.ONE.add(vatRate)).setScale(2, RoundingMode.HALF_UP))
                        .build();

                ProductPrice saved = productPriceRepository.save(newPriceEntity);

                // Also update the base product price so TPV shows the correct price immediately
                product.setPrice(newPrice);
                productRepository.save(product);

                // ── Update TariffPriceHistory for all active tariffs ──
                for (com.proconsi.electrobazar.model.Tariff tariff : allActiveTariffs) {
                    com.proconsi.electrobazar.model.TariffPriceHistory currentHistory = tariffPriceHistoryRepository
                            .findCurrentByProductAndTariff(productId, tariff.getId()).orElse(null);

                    BigDecimal oldBase = currentHistory != null ? currentHistory.getBasePrice() : basePrice;

                    if (currentHistory != null) {
                        currentHistory.setValidTo(closingDate.toLocalDate());
                        tariffPriceHistoryRepository.save(currentHistory);
                    }

                    BigDecimal newTariffBase;
                    if (selectedTariffIds.contains(tariff.getId())) {
                        if (request.getPercentage() != null) {
                            BigDecimal multiplier = BigDecimal.ONE.add(request.getPercentage()
                                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                            newTariffBase = oldBase.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                        } else {
                            newTariffBase = oldBase.add(request.getFixedAmount()).setScale(2, RoundingMode.HALF_UP);
                        }
                    } else {
                        newTariffBase = oldBase;
                    }

                    BigDecimal tariffDiscount = tariff.getDiscountPercentage() != null ? tariff.getDiscountPercentage() : BigDecimal.ZERO;

                    BigDecimal discountMultiplier = BigDecimal.ONE.subtract(tariffDiscount.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                    BigDecimal newNetPrice = newTariffBase.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newPriceWithVat = newNetPrice.multiply(BigDecimal.ONE.add(vatRate)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newReRate = recargoCalculator.getRecargoRate(vatRate);
                    BigDecimal newPriceWithRe = newNetPrice.multiply(BigDecimal.ONE.add(vatRate).add(newReRate)).setScale(2, RoundingMode.HALF_UP);

                    com.proconsi.electrobazar.model.TariffPriceHistory newHistory = com.proconsi.electrobazar.model.TariffPriceHistory.builder()
                            .product(product)
                            .tariff(tariff)
                            .basePrice(newTariffBase)
                            .netPrice(newNetPrice)
                            .vatRate(vatRate)
                            .priceWithVat(newPriceWithVat)
                            .reRate(newReRate)
                            .priceWithRe(newPriceWithRe)
                            .discountPercent(tariffDiscount)
                            .validFrom(effectiveDate.toLocalDate())
                            .validTo(null)
                            .build();

                    tariffPriceHistoryRepository.save(newHistory);
                }

                log.info("Scheduled bulk price update for product '{}' (id={}): {} € (VAT {}%) starting {}",
                        product.getName(), productId,
                        saved.getPrice(), vatRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString(),
                        effectiveDate);

                results.add(toResponse(saved, false));
        }

        activityLogService.logActivity(
                "PROGRAMAR_PRECIOS_MASIVOS",
                "Actualización masiva de precios para " + results.size() + " productos (Efectivo: "
                        + effectiveDate.toString().replace("T", " ") + ")",
                "Admin",
                "PRODUCT",
                null);

        return results;
    }
}
