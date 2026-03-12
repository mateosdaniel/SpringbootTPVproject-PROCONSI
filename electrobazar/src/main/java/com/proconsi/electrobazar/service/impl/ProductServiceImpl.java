package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.repository.ProductPriceRepository;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final ProductPriceRepository productPriceRepository;
    private final com.proconsi.electrobazar.repository.TaxRateRepository taxRateRepository;
    private final com.proconsi.electrobazar.repository.CategoryRepository categoryRepository;
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
    public List<Product> getFilteredProducts(String search, String category, String stock, Boolean active) {
        org.springframework.data.jpa.domain.Specification<Product> spec = com.proconsi.electrobazar.repository.specification.ProductSpecification
                .filterProducts(search, category, stock, active);
        return productRepository.findAll(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id: " + id));
    }

    @Override
    public Product save(Product product) {
        Product saved = productRepository.save(product);
        activityLogService.logActivity(
                "CREAR_PRODUCTO",
                "Nuevo producto añadido: " + saved.getName(),
                "Admin",
                "PRODUCT",
                saved.getId());
        return saved;
    }

    @Override
    public Product update(Long id, com.proconsi.electrobazar.dto.ProductRequest request) {
        Product existing = findById(id);
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        
        // 1. Update Tax Rate first because setPrice depends on it
        if (request.getTaxRateId() != null) {
            com.proconsi.electrobazar.model.TaxRate newRate = taxRateRepository.findById(request.getTaxRateId())
                    .orElseThrow(() -> new ResourceNotFoundException("TaxRate no encontrado: " + request.getTaxRateId()));
            existing.setTaxRate(newRate);
        }

        // 2. Update price using raw values from request to avoid inflation bug
        if (request.getBasePriceNet() != null && request.getBasePriceNet().compareTo(java.math.BigDecimal.ZERO) > 0) {
            existing.setBasePriceNet(request.getBasePriceNet());
        } else if (request.getPrice() != null && request.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            existing.setPrice(request.getPrice());
        }

        if (request.getActive() != null) {
            existing.setActive(request.getActive());
        }
        existing.setImageUrl(request.getImageUrl());
        
        if (request.getCategoryId() != null) {
            existing.setCategory(categoryRepository.findById(request.getCategoryId())
                .orElse(null));
        }

        if (request.getStock() != null && request.getStock() >= 0) {
            existing.setStock(request.getStock());
        }

        Product saved = productRepository.save(existing);

        // 3. Sync BOTH price and vatRate with active temporal price entry
        productPriceRepository.findActivePriceAt(saved.getId(), java.time.LocalDateTime.now())
                .ifPresent(activePrice -> {
                    // Update VAT
                    activePrice.setVatRate(saved.getTaxRate() != null && saved.getTaxRate().getVatRate() != null
                            ? saved.getTaxRate().getVatRate()
                            : new java.math.BigDecimal("0.21"));
                    
                    // Update Price (Gross) - uses saved.getPrice() which is now correct
                    activePrice.setPrice(saved.getPrice());
                    
                    productPriceRepository.save(activePrice);
                    log.info("Synced ProductPrice (id={}) price & vatRate with updated product '{}' (id={}). New Price: {}",
                            activePrice.getId(), saved.getName(), saved.getId(), saved.getPrice());
                });

        activityLogService.logActivity(
                "ACTUALIZAR_PRODUCTO",
                "Producto actualizado: " + saved.getName(),
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
                "Producto dado de baja: " + product.getName(),
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
                "Producto eliminado definitivamente: " + product.getName(),
                "Admin",
                "PRODUCT",
                id);
    }

    @Override
    public void decreaseStock(Long productId, Integer quantity) {
        Product product = findById(productId);
        if (product.getStock() < quantity) {
            throw new IllegalStateException("Stock insuficiente");
        }
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
        activityLogService.logActivity("AJUSTE_STOCK", "Stock reducido (manual): " + product.getName() + " (-" + quantity + ")", "Admin", "PRODUCT", product.getId());
    }

    @Override
    public void increaseStock(Long productId, Integer quantity) {
        Product product = findById(productId);
        product.setStock(product.getStock() + quantity);
        productRepository.save(product);
        activityLogService.logActivity("AJUSTE_STOCK", "Stock incrementado (manual): " + product.getName() + " (+" + quantity + ")", "Admin", "PRODUCT", product.getId());
    }

    @Override
    public void adjustStock(Long productId, Integer quantity) {
        Product product = findById(productId);
        int newStock = product.getStock() + quantity;
        if (newStock < 0) {
            throw new IllegalArgumentException(
                    "El stock no puede ser negativo. Stock actual: " + product.getStock() +
                            ", cambio solicitado: " + quantity);
        }
        product.setStock(newStock);
        productRepository.save(product);
        activityLogService.logActivity("AJUSTE_STOCK", "Ajuste de stock: " + product.getName() + " (Cambio: " + quantity + ", Nuevo stock: " + newStock + ")", "Admin", "PRODUCT", product.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Product> getTopProducts(int limit) {
        return productRepository.findAllActiveWithCategory().stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void applyNewTaxRate(Long newTaxRateId) {
        com.proconsi.electrobazar.model.TaxRate newRate = taxRateRepository.findById(newTaxRateId)
                .orElseThrow(() -> new ResourceNotFoundException("TaxRate no encontrado: " + newTaxRateId));

        // Buscar todos los TaxRates con la misma descripción (ej: "IVA General") pero
        // distinto ID
        List<com.proconsi.electrobazar.model.TaxRate> oldRates = taxRateRepository
                .findByDescriptionAndIdNot(newRate.getDescription(), newTaxRateId);

        if (oldRates.isEmpty())
            return;

        List<Long> oldIds = oldRates.stream().map(com.proconsi.electrobazar.model.TaxRate::getId)
                .collect(java.util.stream.Collectors.toList());

        // Actualizar todos los productos que apuntan a esos IDs viejos
        productRepository.updateTaxRateForIds(oldIds, newRate);

        // Marcar los viejos como finalizados (validTo = ayer)
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        for (com.proconsi.electrobazar.model.TaxRate old : oldRates) {
            old.setValidTo(yesterday);
            old.setActive(false);
            taxRateRepository.save(old);
        }

        activityLogService.logActivity(
                "APLICAR_IVA_MASIVO",
                "Se ha aplicado el nuevo " + newRate.getDescription() + " ("
                        + newRate.getVatRate().multiply(new java.math.BigDecimal(100)) + "%) de forma masiva.",
                "Admin",
                "TAX_RATE",
                newTaxRateId);
    }

    @Override
    @Transactional
    public void applyTaxRateToProducts(List<Long> productIds, com.proconsi.electrobazar.model.TaxRate taxRate) {
        if (productIds == null || productIds.isEmpty()) return;
        List<Product> products = productRepository.findAllById(productIds);
        for (Product product : products) {
            product.setTaxRate(taxRate);
        }
        productRepository.saveAll(products);
    }
}