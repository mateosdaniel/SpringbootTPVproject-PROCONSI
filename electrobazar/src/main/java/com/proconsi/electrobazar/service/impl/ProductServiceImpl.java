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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ProductService}.
 * Core service for product catalog management, stock control, and fiscal rate adjustments.
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

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllActive() {
        return productRepository.findByActiveTrueOrderByNameAsc();
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
        return productRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> search(String name) {
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(name);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findByName(String name) {
        return productRepository.findByNameIgnoreCase(name).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getFilteredProducts(String search, String category, String stock, Boolean active) {
        Specification<Product> spec = ProductSpecification.filterProducts(search, category, stock, active);
        return productRepository.findAll(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Override
    public Product save(Product product) {
        Product saved = productRepository.save(product);
        activityLogService.logActivity(
                "CREAR_PRODUCTO",
                "New product added: " + saved.getName(),
                "Admin",
                "PRODUCT",
                saved.getId());
        return saved;
    }

    @Override
    public Product update(Long id, ProductRequest request) {
        Product existing = findById(id);
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());

        // 1. Update Tax Rate
        if (request.getTaxRateId() != null) {
            TaxRate newRate = taxRateRepository.findById(request.getTaxRateId())
                    .orElseThrow(() -> new ResourceNotFoundException("TaxRate " + request.getTaxRateId() + " not found."));
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

        if (request.getStock() != null && request.getStock() >= 0) {
            existing.setStock(request.getStock());
        }

        Product saved = productRepository.save(existing);

        // 3. Sync with active temporal price entry
        productPriceRepository.findActivePriceAt(saved.getId(), LocalDateTime.now()).ifPresent(active -> {
            active.setVatRate(saved.getTaxRate() != null ? saved.getTaxRate().getVatRate() : new BigDecimal("0.21"));
            active.setPrice(saved.getPrice());
            productPriceRepository.save(active);
        });

        activityLogService.logActivity(
                "ACTUALIZAR_PRODUCTO",
                "Product updated: " + saved.getName(),
                "Admin",
                "PRODUCT",
                saved.getId());
        return saved;
    }

    @Override
    public void delete(Long id) {
        Product product = findById(id);
        product.setActive(false);
        productRepository.save(product);
        activityLogService.logActivity(
                "ELIMINAR_PRODUCTO",
                "Product deactivated: " + product.getName(),
                "Admin",
                "PRODUCT",
                product.getId());
    }

    @Override
    public void hardDeleteProduct(Long id) {
        Product product = findById(id);
        productRepository.deleteById(id);
        activityLogService.logActivity(
                "ELIMINAR_PRODUCTO_HARD",
                "Product permanently deleted: " + product.getName(),
                "Admin",
                "PRODUCT",
                id);
    }

    @Override
    public void decreaseStock(Long productId, Integer quantity) {
        Product product = findById(productId);
        if (product.getStock() < quantity) {
            throw new IllegalStateException("Insufficient stock for product: " + product.getName());
        }
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
        activityLogService.logActivity("AJUSTE_STOCK", 
                "Manual stock decrease: -" + quantity + " for " + product.getName(), "Admin", "PRODUCT", product.getId());
    }

    @Override
    public void increaseStock(Long productId, Integer quantity) {
        Product product = findById(productId);
        product.setStock(product.getStock() + quantity);
        productRepository.save(product);
        activityLogService.logActivity("AJUSTE_STOCK", 
                "Manual stock increase: +" + quantity + " for " + product.getName(), "Admin", "PRODUCT", product.getId());
    }

    @Override
    public void adjustStock(Long productId, Integer quantity) {
        Product product = findById(productId);
        int newStock = product.getStock() + quantity;
        if (newStock < 0) {
            throw new IllegalArgumentException("Target stock cannot be negative.");
        }
        product.setStock(newStock);
        productRepository.save(product);
        activityLogService.logActivity("AJUSTE_STOCK", 
                "Stock adjustment: " + quantity + " (New stock: " + newStock + ") for " + product.getName(), "Admin", "PRODUCT", product.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getTopProducts(int limit) {
        return productRepository.findAllActiveWithCategory().stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void applyNewTaxRate(Long newTaxRateId) {
        TaxRate newRate = taxRateRepository.findById(newTaxRateId)
                .orElseThrow(() -> new ResourceNotFoundException("TaxRate " + newTaxRateId + " not found."));

        List<TaxRate> oldRates = taxRateRepository.findByDescriptionAndIdNot(newRate.getDescription(), newTaxRateId);
        if (oldRates.isEmpty()) return;

        List<Long> oldIds = oldRates.stream().map(TaxRate::getId).collect(Collectors.toList());
        productRepository.updateTaxRateForIds(oldIds, newRate);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        for (TaxRate old : oldRates) {
            old.setValidTo(yesterday);
            old.setActive(false);
            taxRateRepository.save(old);
        }

        activityLogService.logActivity("APLICAR_IVA_MASIVO", 
                "Bulk VAT update: " + newRate.getDescription() + " (applied to all matching products)", "Admin", "TAX_RATE", newTaxRateId);
    }

    @Override
    @Transactional
    public void applyTaxRateToProducts(List<Long> productIds, TaxRate taxRate) {
        if (productIds == null || productIds.isEmpty()) return;
        List<Product> products = productRepository.findAllById(productIds);
        products.forEach(p -> p.setTaxRate(taxRate));
        productRepository.saveAll(products);
    }
}