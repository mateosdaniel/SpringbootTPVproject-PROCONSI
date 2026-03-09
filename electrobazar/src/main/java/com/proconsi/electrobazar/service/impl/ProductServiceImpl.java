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
    public Product update(Long id, Product updated) {
        Product existing = findById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setIvaRate(updated.getIvaRate()); // Set rate first

        // If the update object came with a specific net base price, use it
        if (updated.getBasePriceNet() != null && updated.getBasePriceNet().compareTo(java.math.BigDecimal.ZERO) > 0) {
            existing.setBasePriceNet(updated.getBasePriceNet());
        } else {
            existing.setPrice(updated.getPrice());
        }

        existing.setActive(updated.getActive());
        existing.setImageUrl(updated.getImageUrl());
        existing.setCategory(updated.getCategory());

        // El stock se puede actualizar manualmente para corregir errores de datos
        if (updated.getStock() != null && updated.getStock() >= 0) {
            existing.setStock(updated.getStock());
        }
        Product saved = productRepository.save(existing);

        // Sync active temporal price with the new product VAT rate
        productPriceRepository.findActivePriceAt(saved.getId(), java.time.LocalDateTime.now())
                .ifPresent(activePrice -> {
                    activePrice.setVatRate(saved.getIvaRate());
                    productPriceRepository.save(activePrice);
                    log.info("Synced ProductPrice (id={}) vatRate with product '{}' (id={}) updated ivaRate: {}",
                            activePrice.getId(), saved.getName(), saved.getId(), saved.getIvaRate());
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
    }

    @Override
    public void increaseStock(Long productId, Integer quantity) {
        Product product = findById(productId);
        product.setStock(product.getStock() + quantity);
        productRepository.save(product);
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
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Product> getTopProducts(int limit) {
        return productRepository.findAllActiveWithCategory().stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }
}