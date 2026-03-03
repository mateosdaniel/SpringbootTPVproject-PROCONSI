package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.ProductRequest;
import com.proconsi.electrobazar.dto.ProductSelectionItem;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.service.CategoryService;
import com.proconsi.electrobazar.service.ProductPriceService;
import com.proconsi.electrobazar.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiRestController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductPriceService productPriceService;

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

    @GetMapping("/filter")
    public ResponseEntity<List<Product>> filterProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) Boolean active) {

        return ResponseEntity.ok(productService.getFilteredProducts(search, category, stock, active));
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setIvaRate(request.getIvaRate());
        product.setStock(request.getStock() != null ? request.getStock() : 0);
        product.setImageUrl(request.getImageUrl());

        if (request.getCategoryId() != null) {
            product.setCategory(categoryService.findById(request.getCategoryId()));
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

    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        productService.hardDeleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/adjust-stock")
    public ResponseEntity<Void> adjustStock(@PathVariable Long id, @RequestParam Integer quantity) {
        productService.adjustStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns a list of products for frontend selection UI with current prices and
     * VAT.
     *
     * <p>
     * Each item contains: id, name, currentPrice, currentVat, categoryId,
     * categoryName.
     * This endpoint is designed for the bulk price update UI.
     * </p>
     *
     * <p>
     * Example: {@code GET /api/products/selection-list}
     * </p>
     *
     * @return 200 with list of {@link ProductSelectionItem}
     */
    @GetMapping("/selection-list")
    public ResponseEntity<List<ProductSelectionItem>> getSelectionList() {
        List<Product> products = productService.findAllActiveWithCategory();
        LocalDateTime now = LocalDateTime.now();

        List<ProductSelectionItem> selectionItems = products.stream()
                .map(product -> {
                    BigDecimal currentPrice;
                    BigDecimal currentVat;

                    // Try to get current price from price history
                    var priceEntity = productPriceService.getCurrentPrice(product.getId(), now);
                    if (priceEntity != null) {
                        currentPrice = priceEntity.getPrice();
                        currentVat = priceEntity.getVatRate();
                    } else {
                        // Fall back to product's base price and ivaRate
                        currentPrice = product.getPrice();
                        currentVat = product.getIvaRate() != null ? product.getIvaRate() : new BigDecimal("0.21");
                    }

                    return new ProductSelectionItem(
                            product.getId(),
                            product.getName(),
                            currentPrice,
                            currentVat,
                            product.getCategory() != null ? product.getCategory().getId() : null,
                            product.getCategory() != null ? product.getCategory().getName() : null);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(selectionItems);
    }
}