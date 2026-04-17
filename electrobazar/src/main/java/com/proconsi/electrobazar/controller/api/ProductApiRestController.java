package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.ProductRequest;
import com.proconsi.electrobazar.dto.ProductSelectionItem;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.ProductPrice;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.UserFavoriteProduct;
import com.proconsi.electrobazar.service.CategoryService;
import com.proconsi.electrobazar.service.ProductPriceService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import com.proconsi.electrobazar.repository.MeasurementUnitRepository;
import com.proconsi.electrobazar.repository.UserFavoriteProductRepository;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final WorkerService workerService;
    private final TaxRateRepository taxRateRepository;
    private final MeasurementUnitRepository measurementUnitRepository;
    private final UserFavoriteProductRepository userFavoriteProductRepository;

    /**
     * Retrieves all active products including their category details.
     * 
     * @return List of active {@link Product} entities.
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(productService.getTopProductsByRank(100));
    }

    /**
     * Retrieves a single product by its ID.
     * 
     * @param id The product ID.
     * @return The requested {@link Product}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    /**
     * Retrieves all products belonging to a specific category.
     * 
     * @param categoryId The category ID.
     * @return List of products.
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(productService.findByCategory(categoryId));
    }

    /**
     * Performs a text-based search on product names with pagination.
     * 
     * @param name The search query.
     * @param page Page number (0-indexed).
     * @param size Number of items per page.
     * @return Page of matching products.
     */
    @GetMapping("/search")
    public ResponseEntity<org.springframework.data.domain.Page<Product>> search(
            @RequestParam(value = "q") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(productService.getFilteredProducts(q, null, null, true, null, pageable));
    }

    /**
     * Advanced filtering for products with pagination.
     * 
     * @return Page of filtered products.
     */
    @GetMapping("/filter")
    public ResponseEntity<org.springframework.data.domain.Page<Product>> filterProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long unitId, // Added
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(productService.getFilteredProducts(search, category, stock, active, unitId, pageable));
    }

    /**
     * Creates a new product.
     * Validates that the provided tax rate ID exists before persisting.
     * 
     * @param request The product details.
     * @return 201 Created with the saved {@link Product}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Product> create(
            @Valid @ModelAttribute ProductRequest request,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
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

        product.setStock(request.getStock() != null ? request.getStock() : BigDecimal.ZERO);
        
        // Handle image upload
        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = saveImage(imageFile);
            product.setImageUrl(imageUrl);
        } else {
            product.setImageUrl(request.getImageUrl());
        }

        if (request.getMeasurementUnitId() != null) {
            product.setMeasurementUnit(measurementUnitRepository.findById(request.getMeasurementUnitId()).orElse(null));
        }

        if (request.getCategoryId() != null) {
            product.setCategory(categoryService.findById(request.getCategoryId()));
        }
        log.info("Saving product with taxRate: {}", product.getTaxRate());
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.save(product));
    }

    /**
     * Updates an existing product's details.
     * 
     * @param id      The product ID.
     * @param request The new product details.
     * @return 200 OK with the updated {@link Product}.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Product> update(
            @PathVariable Long id,
            @Valid @ModelAttribute ProductRequest request,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        log.info("Updating product {} with request: {}", id, request);
        
        // If there's a new image, handle it before updating the service
        if (imageFile != null && !imageFile.isEmpty()) {
            request.setImageUrl(saveImage(imageFile));
        }
        
        return ResponseEntity.ok(productService.update(id, request));
    }

    private String saveImage(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            String uploadDir = "src/main/resources/static/img/";
            Path uploadPath = Paths.get(uploadDir);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            try (var inputStream = file.getInputStream()) {
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(inputStream, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return "/img/" + fileName;
            }
        } catch (IOException e) {
            log.error("Error saving image", e);
            return null;
        }
    }

    /**
     * Deactivates a product (Soft Delete).
     * 
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
     * 
     * @param id       The product ID.
     * @param quantity The amount to add (positive) or subtract (negative).
     * @return 200 OK.
     */
    @PostMapping("/{id}/adjust-stock")
    public ResponseEntity<Void> adjustStock(@PathVariable Long id, @RequestParam BigDecimal quantity) {
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
        List<Product> products = productService.getTopProductsByRank(100);
        LocalDateTime now = LocalDateTime.now();

        // 1. Bulk fetch active prices to avoid N+1 queries
        List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        List<ProductPrice> activePrices = productPriceService.getActivePrices(productIds, now);

        // 2. Map prices for efficient lookup
        Map<Long, ProductPrice> priceMap = activePrices.stream()
                .collect(Collectors.toMap(p -> p.getProduct().getId(), p -> p, (a, b) -> a));

        List<ProductSelectionItem> selectionItems = products.stream()
                .map(product -> {
                    BigDecimal currentPrice = product.getPrice();
                    BigDecimal currentVat = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null
                            ? product.getTaxRate().getVatRate()
                            : new BigDecimal("0.21");

                    ProductPrice priceEntity = priceMap.get(product.getId());
                    if (priceEntity != null) {
                        currentPrice = priceEntity.getPrice();
                        currentVat = priceEntity.getVatRate();
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

    @PostMapping("/{productId}/favorite")
    @Transactional
    public ResponseEntity<Void> addFavorite(@PathVariable Long productId) {
        Long userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (!userFavoriteProductRepository.findProductIdsByUserId(userId).contains(productId)) {
            userFavoriteProductRepository.save(UserFavoriteProduct.builder()
                    .userId(userId)
                    .productId(productId)
                    .build());
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{productId}/favorite")
    @Transactional
    public ResponseEntity<Void> removeFavorite(@PathVariable Long productId) {
        Long userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        userFavoriteProductRepository.deleteByUserIdAndProductId(userId, productId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/favorites")
    public ResponseEntity<List<Long>> getFavorites() {
        Long userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(userFavoriteProductRepository.findProductIdsByUserId(userId));
    }

    @GetMapping("/future-prices")
    public ResponseEntity<List<com.proconsi.electrobazar.dto.ProductPriceResponse>> getFuturePrices(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(productPriceService.getFilteredFuturePrices(search, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent().stream()
                .map(p -> productPriceService.toResponse(p, false))
                .collect(Collectors.toList()));
    }

    @GetMapping("/bulk-list")
    public ResponseEntity<List<com.proconsi.electrobazar.dto.AdminProductListingDTO>> getBulkProductList() {
        return ResponseEntity.ok(productService.getTopProductsByRank(100).stream()
                .map(p -> com.proconsi.electrobazar.dto.AdminProductListingDTO.builder()
                        .id(p.getId())
                        .name(p.getNameEs())
                        .price(p.getPrice())
                        .categoryName(p.getCategory() != null ? p.getCategory().getNameEs() : null)
                        .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                        .build())
                .collect(Collectors.toList()));
    }

    @PostMapping("/bulk-update")
    public ResponseEntity<?> bulkPriceMassiveUpdate(@RequestBody com.proconsi.electrobazar.dto.BulkPriceUpdateRequest request) {
        productPriceService.bulkSchedulePrice(request);
        return ResponseEntity.ok(Map.of("message", "Actualización masiva programada con éxito."));
    }

    private final com.proconsi.electrobazar.service.TaskProgressService taskProgressService;

    @GetMapping("/bulk-progress/{taskId}")
    public ResponseEntity<?> getBulkProgress(@PathVariable String taskId) {
        return ResponseEntity.ok(taskProgressService.getProgress(taskId));
    }

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return workerService.findByUsername(username).map(Worker::getId).orElse(null);
    }
}