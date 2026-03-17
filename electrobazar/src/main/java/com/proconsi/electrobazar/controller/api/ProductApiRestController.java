package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.ProductRequest;
import com.proconsi.electrobazar.dto.ProductSelectionItem;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.service.CategoryService;
import com.proconsi.electrobazar.service.ProductPriceService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for managing the Product catalog.
 * Provides endpoints for CRUD operations, advanced filtering, and integration 
 * with the temporal pricing system.
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiRestController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductPriceService productPriceService;
    private final TaxRateRepository taxRateRepository;

    /**
     * Retrieves all active products including their category details.
     * @return List of active {@link Product} entities.
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(productService.findAllActiveWithCategory());
    }

    /**
     * Retrieves a single product by its ID.
     * @param id The product ID.
     * @return The requested {@link Product}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    /**
     * Retrieves all products belonging to a specific category.
     * @param categoryId The category ID.
     * @return List of products.
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.findByCategory(categoryId));
    }

    /**
     * Performs a text-based search on product names.
     * @param name The search query.
     * @return List of matching products.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Product>> search(@RequestParam String name) {
        return ResponseEntity.ok(productService.search(name));
    }

    /**
     * Advanced filtering for products based on name, category, stock levels, and active status.
     * @return List of filtered products.
     */
    @GetMapping("/filter")
    public ResponseEntity<List<Product>> filterProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) Boolean active) {

        return ResponseEntity.ok(productService.getFilteredProducts(search, category, stock, active));
    }

    /**
     * Creates a new product.
     * Validates that the provided tax rate ID exists before persisting.
     * 
     * @param request The product details.
     * @return 201 Created with the saved {@link Product}.
     */
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
        log.info("Creating product with request: {}", request);
        
        if (request.getTaxRateId() == null) {
            log.error("taxRateId is null in request");
            return ResponseEntity.badRequest().build();
        }
        
        var taxRateOpt = taxRateRepository.findById(request.getTaxRateId());
        if (taxRateOpt.isEmpty()) {
            log.error("TaxRate not found with id: {}", request.getTaxRateId());
            throw new ResourceNotFoundException("TaxRate no encontrado con id: " + request.getTaxRateId());
        }
        
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setTaxRate(taxRateOpt.get());
        
        if (request.getBasePriceNet() != null) {
            product.setBasePriceNet(request.getBasePriceNet());
        } else {
            product.setPrice(request.getPrice());
        }

        product.setStock(request.getStock() != null ? request.getStock() : 0);
        product.setImageUrl(request.getImageUrl());

        if (request.getCategoryId() != null) {
            product.setCategory(categoryService.findById(request.getCategoryId()));
        }
        log.info("Saving product with taxRate: {}", product.getTaxRate());
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.save(product));
    }

    /**
     * Updates an existing product's details.
     * @param id The product ID.
     * @param request The new product details.
     * @return 200 OK with the updated {@link Product}.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        log.info("Updating product {} with request: {}", id, request);
        return ResponseEntity.ok(productService.update(id, request));
    }

    /**
     * Deactivates a product (Soft Delete).
     * @param id The product ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Permanently removes a product from the database (Hard Delete).
     * Use with caution as it may break referential integrity in old sales.
     * 
     * @param id The product ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}/hard")
    public ResponseEntity<Void> hardDelete(@PathVariable Long id) {
        productService.hardDeleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Manually adjusts the stock level of a product.
     * @param id The product ID.
     * @param quantity The amount to add (positive) or subtract (negative).
     * @return 200 OK.
     */
    @PostMapping("/{id}/adjust-stock")
    public ResponseEntity<Void> adjustStock(@PathVariable Long id, @RequestParam Integer quantity) {
        productService.adjustStock(id, quantity);
        return ResponseEntity.ok().build();
    }

    /**
     * Returns a lightweight list of products optimized for selection UIs.
     * Dynamically calculates current prices based on active temporal pricing.
     * 
     * @return List of {@link ProductSelectionItem} for frontend dropdowns/pickers.
     */
    @GetMapping("/selection-list")
    public ResponseEntity<List<ProductSelectionItem>> getSelectionList() {
        List<Product> products = productService.findAllActiveWithCategory();
        LocalDateTime now = LocalDateTime.now();

        List<ProductSelectionItem> selectionItems = products.stream()
                .map(product -> {
                    BigDecimal currentPrice;
                    BigDecimal currentVat;

                    ProductPrice priceEntity = productPriceService.getCurrentPrice(product.getId(), now);
                    if (priceEntity != null) {
                        currentPrice = priceEntity.getPrice();
                        currentVat = priceEntity.getVatRate();
                    } else {
                        currentPrice = product.getPrice();
                        currentVat = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null 
                            ? product.getTaxRate().getVatRate() : new BigDecimal("0.21");
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