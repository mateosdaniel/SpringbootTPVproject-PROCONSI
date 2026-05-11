package com.proconsi.electrobazar.controller.api.admin;

import com.proconsi.electrobazar.dto.*;
import com.proconsi.electrobazar.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for managing products, categories, and bulk price updates.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminProductsApiController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductPriceService productPriceService;

    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> getProductsPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String stock,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "nameEs", "price", "stock", "category.nameEs", "salesRank");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable;
        if (search != null && !search.trim().isEmpty() && ("id".equals(sortBy) || "nameEs".equals(sortBy))) {
            pageable = PageRequest.of(page, size);
        } else {
            pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        }
        org.springframework.data.domain.Slice<com.proconsi.electrobazar.model.Product> productsSlice = productService.findAdminListing(search, category, stock, active, unitId, pageable);

        List<AdminProductListingDTO> list = productsSlice.getContent().stream().map(p -> AdminProductListingDTO.builder()
                .id(p.getId())
                .name(p.getNameEs())
                .description(p.getDescriptionEs())
                .price(p.getPrice())
                .stock(p.getStock())
                .categoryName(p.getCategory() != null ? p.getCategory().getNameEs() : null)
                .measurementUnit(p.getMeasurementUnit())
                .priceDecimals(p.getMeasurementUnit() != null ? Math.max(2, p.getMeasurementUnit().getDecimalPlaces()) : 2)
                .vatRate(p.getTaxRate() != null ? p.getTaxRate().getVatRate() : null)
                .imageUrl(p.getImageUrl())
                .active(Boolean.TRUE.equals(p.getActive()))
                .salesRank(p.getSalesRank())
                .build()).collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", productsSlice.getNumber());
        response.put("hasNext", productsSlice.hasNext());
        response.put("first", productsSlice.isFirst());
        response.put("last", !productsSlice.hasNext());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategoriesPage(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "nameEs", "descriptionEs");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        org.springframework.data.domain.Slice<com.proconsi.electrobazar.model.Category> categoriesSlice = categoryService.getFilteredCategories(search, pageable);

        List<AdminCategoryListingDTO> list = categoriesSlice.getContent().stream()
                .map(c -> AdminCategoryListingDTO.builder()
                        .id(c.getId())
                        .name(c.getNameEs())
                        .description(c.getDescriptionEs())
                        .active(Boolean.TRUE.equals(c.getActive()))
                        .build())
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("content", list);
        response.put("number", categoriesSlice.getNumber());
        response.put("hasNext", categoriesSlice.hasNext());
        response.put("first", categoriesSlice.isFirst());
        response.put("last", !categoriesSlice.hasNext());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tax-rates/{newId}/apply-to-products")
    public ResponseEntity<?> applyNewTaxRate(@PathVariable Long newId) {
        productService.applyNewTaxRate(newId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk-price-update")
    public ResponseEntity<?> bulkPriceUpdate(@RequestBody BulkPriceMatrixUpdateRequest request) {
        productPriceService.bulkMatrixUpdate(request);
        return ResponseEntity.ok(Map.of("message", "Precios procesados correctamente."));
    }

    @GetMapping("/price-updates/pending")
    public ResponseEntity<List<PriceMatrixSummaryDTO>> getPendingPriceUpdates() {
        return ResponseEntity.ok(productPriceService.getPendingMatrixUpdates());
    }

    @GetMapping("/price-updates/history")
    public ResponseEntity<List<PriceMatrixSummaryDTO>> getPriceUpdateHistory() {
        return ResponseEntity.ok(productPriceService.getMatrixUpdateHistory());
    }

    @DeleteMapping("/price-updates/{id}")
    public ResponseEntity<?> deletePendingPriceUpdate(@PathVariable Long id) {
        productPriceService.deletePendingPrice(id);
        return ResponseEntity.ok().build();
    }
}
