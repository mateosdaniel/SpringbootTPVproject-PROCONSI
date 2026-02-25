package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.service.CategoryService;
import com.proconsi.electrobazar.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiRestController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(productService.findAllActiveWithCategory());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.findByCategory(categoryId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> search(@RequestParam String name) {
        return ResponseEntity.ok(productService.search(name));
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            product.setCategory(categoryService.findById(product.getCategory().getId()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.save(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            product.setCategory(categoryService.findById(product.getCategory().getId()));
        }
        return ResponseEntity.ok(productService.update(id, product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}