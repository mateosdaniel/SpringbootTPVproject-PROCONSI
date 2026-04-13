package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.ProductRequest;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.repository.CategoryRepository;
import com.proconsi.electrobazar.repository.ProductPriceRepository;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import com.proconsi.electrobazar.repository.specification.ProductSpecification;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.TariffService;
import com.proconsi.electrobazar.repository.SaleLineRepository;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ProductService}.
 * Core service for product catalog management, stock control, and fiscal rate
 * adjustments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final TaxRateRepository taxRateRepository;
    private final CategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;
    private final TariffService tariffService;
    private final SaleLineRepository saleLineRepository;
    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final com.proconsi.electrobazar.repository.MeasurementUnitRepository measurementUnitRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllActive() {
        return productRepository.findByActiveTrueOrderByNameEsAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllActiveWithCategory() {
        return productRepository.findAllActiveWithCategory();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllWithCategory() {
        return productRepository.findAllWithCategory();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findByCategory(Long categoryId) {
        return productRepository.findByCategoryIdAndActiveTrueOrderByNameEsAsc(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> search(String name) {
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findByName(String name) {
        return productRepository.findByNameEsIgnoreCase(name).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getFilteredProducts(String search, String category, String stock, Boolean active) {
        Specification<Product> spec = ProductSpecification.filterProducts(search, category, stock, active);
        return productRepository.findAll(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Product> getFilteredProducts(String search, String category, String stock, Boolean active,
            Pageable pageable) {
        Specification<Product> spec = ProductSpecification.filterProducts(search, category, stock, active);
        return productRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty())
            return List.of();
        return productRepository.findAllById(ids);
    }

    @Override
    public Product save(Product product) {
        // autoTranslateProduct(product);
        Product saved = productRepository.save(product);

        // Ensure the product is immediately included in tariff price history/PDFs
        try {
            tariffService.regenerateTariffHistoryForProducts(java.util.List.of(saved));
        } catch (Exception e) {
            log.error("Error generating initial tariff history for new product {}: {}", saved.getName(),
                    e.getMessage());
        }

        activityLogService.logActivity(
                "CREAR_PRODUCTO",
                "Nuevo producto añadido: " + saved.getName(),
                "Admin",
                "PRODUCT",
                saved.getId());
        return saved;
    }

    @Override
    public Product update(Long id, ProductRequest request) {
        Product existing = findById(id);
        existing.setNameEs(request.getName());
        existing.setDescriptionEs(request.getDescription());

        // Trigger auto-translation on update (Currently disabled by request: no API
        // Key)
        // autoTranslateProduct(existing);

        // 1. Update Tax Rate
        if (request.getTaxRateId() != null) {
            TaxRate newRate = taxRateRepository.findById(request.getTaxRateId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("TaxRate " + request.getTaxRateId() + " not found."));
            existing.setTaxRate(newRate);
        }

        // 2. Update Price
        if (request.getBasePriceNet() != null && request.getBasePriceNet().compareTo(BigDecimal.ZERO) > 0) {
            existing.setBasePriceNet(request.getBasePriceNet());
        } else if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            existing.setPrice(request.getPrice());
        }

        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }
        existing.setImageUrl(request.getImageUrl());

        if (request.getCategoryId() != null) {
            existing.setCategory(categoryRepository.findById(request.getCategoryId()).orElse(null));
        }

        if (request.getMeasurementUnitId() != null) {
            existing.setMeasurementUnit(
                    measurementUnitRepository.findById(request.getMeasurementUnitId()).orElse(null));
        }

        if (request.getStock() != null && request.getStock().compareTo(BigDecimal.ZERO) >= 0) {
            existing.setStock(request.getStock());
        }

        Product saved = productRepository.save(existing);

        // 3. Sync with active temporal price entry
        productPriceRepository.findActivePriceAt(saved.getId(), LocalDateTime.now()).ifPresent(active -> {
            active.setVatRate(saved.getTaxRate() != null ? saved.getTaxRate().getVatRate() : new BigDecimal("0.21"));
            active.setPrice(saved.getPrice());
            productPriceRepository.save(active);
        });

        // 4. Update tariff history to reflect price/VAT changes in reports/PDFs
        try {
            tariffService.regenerateTariffHistoryForProducts(java.util.List.of(saved));
        } catch (Exception e) {
            log.error("Error updating tariff history for product {}: {}", saved.getName(), e.getMessage());
        }

        activityLogService.logActivity(
                "ACTUALIZAR_PRODUCTO",
                "Producto actualizado: " + saved.getName(),
                "Admin",
                "PRODUCT",
                saved.getId());
        return saved;
    }

    /*
     * private void autoTranslateProduct(Product product) {
     * if (product.getNameEs() == null || product.getNameEs().isBlank()) return;
     * ...
     * }
     */

    @Override
    public void delete(Long id) {
        Product product = findById(id);
        product.setActive(false);
        productRepository.save(product);
        activityLogService.logActivity(
                "ELIMINAR_PRODUCTO",
                "Producto desactivado: " + product.getName(),
                "Admin",
                "PRODUCT",
                product.getId());
    }

    @Override
    @Transactional
    public void hardDeleteProduct(Long id) {
        Product product = findById(id);

        // 1. Check for real transaction references
        if (saleLineRepository.countByProductId(id) > 0) {
            throw new IllegalStateException(
                    "Cannot delete item because it is referenced by other records (sales, invoices, etc.).");
        }

        // 2. Clear metadata/history cascade manually if not mapped as cascade
        productPriceRepository.deleteByProductId(id);
        tariffPriceHistoryRepository.deleteByProductId(id);

        // 3. Final Delete
        productRepository.deleteById(id);

        activityLogService.logActivity(
                "ELIMINAR_PRODUCTO_HARD",
                "Producto eliminado permanentemente: " + product.getName(),
                "Admin",
                "PRODUCT",
                id);
    }

    @Override
    @Transactional
    public void decreaseStock(Long productId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            return;

        int updated = productRepository.decreaseStockAtomic(productId, quantity);
        if (updated == 0) {
            Product p = findById(productId);
            throw new IllegalStateException("Insufficient stock for product: " + p.getName());
        }

        // Audit log (only once per call)
        activityLogService.logActivity("AJUSTE_STOCK",
                "Disminución manual de stock: -" + quantity + " para el producto ID: " + productId, "Admin", "PRODUCT",
                productId);
    }

    @Override
    @Transactional
    public void increaseStock(Long productId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
            return;
        productRepository.increaseStockAtomic(productId, quantity);
    }

    @Override
    public void adjustStock(Long productId, BigDecimal quantity) {
        Product product = findById(productId);
        BigDecimal newStock = product.getStock().add(quantity);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Target stock cannot be negative.");
        }
        product.setStock(newStock);
        productRepository.save(product);
        activityLogService.logActivity("AJUSTE_STOCK",
                "Ajuste de stock: " + quantity + " (Nuevo stock: " + newStock + ") para " + product.getName(), "Admin",
                "PRODUCT", product.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getTopProducts(int limit) {
        // Use salesRank + alphabetical fallback
        return productRepository.findTop100BySalesRank(org.springframework.data.domain.PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void applyNewTaxRate(Long newTaxRateId) {
        TaxRate newRate = taxRateRepository.findById(newTaxRateId)
                .orElseThrow(() -> new ResourceNotFoundException("TaxRate " + newTaxRateId + " not found."));

        List<TaxRate> oldRates = taxRateRepository.findByDescriptionAndIdNot(newRate.getDescription(), newTaxRateId);
        if (oldRates.isEmpty())
            return;

        List<Long> oldIds = oldRates.stream().map(TaxRate::getId).collect(Collectors.toList());
        productRepository.updateTaxRateForIds(oldIds, newRate);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        for (TaxRate old : oldRates) {
            old.setValidTo(yesterday);
            old.setActive(false);
            taxRateRepository.save(old);
        }

        activityLogService.logActivity("APLICAR_IVA_MASIVO",
                "Actualización masiva de IVA: " + newRate.getDescription()
                        + " (aplicado a todos los productos coincidentes)",
                "Admin", "TAX_RATE", newTaxRateId);
    }

    @Override
    @Transactional
    public void applyTaxRateToProducts(List<Long> productIds, TaxRate taxRate) {
        if (productIds == null || productIds.isEmpty())
            return;
        List<Product> products = productRepository.findAllById(productIds);
        products.forEach(p -> p.setTaxRate(taxRate));
        productRepository.saveAll(products);
    }

    @Override
    @org.springframework.cache.annotation.CacheEvict(value = "productPrices", allEntries = true)
    public void recalculatePricesForTaxRate(Long taxRateId, BigDecimal newVatRate) {
        productRepository.updateGrossPricesByTaxRate(taxRateId, newVatRate);
    }

    @Override
    public Page<Product> findAllWithCategoryPaged(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 25 : size;

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("nameEs").ascending());
        return productRepository.findAllWithCategoryPaged(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getTopSellingProducts(int limit) {
        return productRepository.findTopSellingProducts(PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getTopProductsByRank(int limit) {
        return productRepository.findTop100BySalesRank(org.springframework.data.domain.PageRequest.of(0, limit));
    }
}