package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Product;
import java.util.List;

public interface ProductService {
    List<Product> findAll();
    List<Product> findAllActive();
    List<Product> findAllActiveWithCategory();
    List<Product> findByCategory(Long categoryId);
    List<Product> search(String name);
    Product findById(Long id);
    Product findByBarcode(String barcode);
    Product save(Product product);
    Product update(Long id, Product product);
    void delete(Long id);
    
    // Stock management
    void decreaseStock(Long productId, Integer quantity);
    void increaseStock(Long productId, Integer quantity);
    void adjustStock(Long productId, Integer quantity);
}