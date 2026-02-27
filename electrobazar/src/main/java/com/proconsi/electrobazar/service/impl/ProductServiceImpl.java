package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
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
        existing.setPrice(updated.getPrice());
        existing.setActive(updated.getActive());
        existing.setImageUrl(updated.getImageUrl());
        existing.setCategory(updated.getCategory());
        // Stock es gestionado únicamente a través de métodos específicos de stock
        // no se actualiza en el método update

        Product saved = productRepository.save(existing);
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
}