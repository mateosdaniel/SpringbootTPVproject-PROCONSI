package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.DuplicateResourceException;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.repository.ProductRepository;
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
    @Transactional(readOnly = true)
    public Product findByBarcode(String barcode) {
        return productRepository.findByBarcodeAndActiveTrue(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con código de barras: " + barcode));
    }

    @Override
    public Product save(Product product) {
        if (product.getBarcode() != null && !product.getBarcode().isBlank()
                && productRepository.existsByBarcodeAndIdNot(product.getBarcode(), -1L)) {
            throw new DuplicateResourceException("Ya existe un producto con el código de barras: " + product.getBarcode());
        }
        return productRepository.save(product);
    }

    @Override
    public Product update(Long id, Product updated) {
        Product existing = findById(id);

        if (updated.getBarcode() != null && !updated.getBarcode().isBlank()
                && productRepository.existsByBarcodeAndIdNot(updated.getBarcode(), id)) {
            throw new DuplicateResourceException("Ya existe un producto con el código de barras: " + updated.getBarcode());
        }

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setPrice(updated.getPrice());
        existing.setBarcode(updated.getBarcode());
        existing.setActive(updated.getActive());
        existing.setCategory(updated.getCategory());

        return productRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        Product product = findById(id);
        product.setActive(false);
        productRepository.save(product);
    }
}