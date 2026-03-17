package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.TariffService;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for managing Spanish Tax Rates (VAT and RE).
 * Handles the creation of new tax rules and bulk application of these rates to 
 * the product catalog.
 */
@RestController
@RequestMapping("/admin/api/tax-rates")
@RequiredArgsConstructor
public class TaxRateApiRestController {

    private final TaxRateRepository taxRateRepository;
    private final ActivityLogService activityLogService;
    private final ProductService productService;
    private final TariffService tariffService;
    private final ProductRepository productRepository;

    /**
     * Retrieves all configured tax rates.
     * @return List of all {@link TaxRate} entries.
     */
    @GetMapping
    public List<TaxRate> getAll() {
        return taxRateRepository.findAll();
    }

    /**
     * Retrieves currently active tax rates.
     * @return List of active {@link TaxRate} entries.
     */
    @GetMapping("/active")
    public List<TaxRate> getActive() {
        return taxRateRepository.findByActiveTrue();
    }

    /**
     * Creates a new Spanish Tax Rate.
     * Automatically invalidates (sets 'validTo') of previous rates with the same description 
     * to maintain temporal integrity.
     * 
     * @param taxRate The new tax rate details.
     * @return The saved {@link TaxRate}.
     */
    @PostMapping
    public TaxRate create(@Valid @RequestBody TaxRate taxRate) {
        if (taxRate.getValidFrom() != null) {
            taxRateRepository.findByActiveTrue().stream()
                .filter(tr -> tr.getDescription() != null && tr.getDescription().equals(taxRate.getDescription()))
                .forEach(tr -> {
                    tr.setValidTo(taxRate.getValidFrom().minusDays(1));
                    taxRateRepository.save(tr);
                });
        }
        TaxRate saved = taxRateRepository.save(taxRate);
        activityLogService.logActivity("CREAR_IVA", "Nuevo tipo de IVA creado: " + saved.getDescription() + " (" + saved.getVatRate().multiply(new BigDecimal("100")) + "%)", "Admin", "TAX_RATE", saved.getId());
        return saved;
    }

    /**
     * Updates an existing tax rate configuration.
     * @param id The tax rate ID.
     * @param taxRate New details.
     * @return 200 OK with the updated {@link TaxRate}.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaxRate> update(@PathVariable Long id, @Valid @RequestBody TaxRate taxRate) {
        return taxRateRepository.findById(id).map(existing -> {
            existing.setVatRate(taxRate.getVatRate());
            existing.setReRate(taxRate.getReRate());
            existing.setDescription(taxRate.getDescription());
            existing.setActive(taxRate.getActive());
            existing.setValidFrom(taxRate.getValidFrom());
            existing.setValidTo(taxRate.getValidTo());

            if (taxRate.getValidFrom() != null) {
                taxRateRepository.findByActiveTrue().stream()
                    .filter(tr -> !tr.getId().equals(id) && tr.getDescription() != null && tr.getDescription().equals(taxRate.getDescription()))
                    .forEach(tr -> {
                        tr.setValidTo(taxRate.getValidFrom().minusDays(1));
                        taxRateRepository.save(tr);
                    });
            }

            TaxRate saved = taxRateRepository.save(existing);
            activityLogService.logActivity("ACTUALIZAR_IVA", "Tipo de IVA actualizado: " + saved.getDescription() + " (" + saved.getVatRate().multiply(new BigDecimal("100")) + "%)", "Admin", "TAX_RATE", saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a tax rate configuration.
     * @param id The ID to delete.
     * @return 200 OK.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taxRateRepository.findById(id).ifPresent(tr -> {
            taxRateRepository.deleteById(id);
            activityLogService.logActivity("ELIMINAR_IVA", "Tipo de IVA eliminado: " + tr.getDescription(), "Admin", "TAX_RATE", id);
        });
        return ResponseEntity.ok().build();
    }

    /**
     * Applies a specific tax rate to a selection of products or entire categories.
     * This bulk operation updates product links and regenerates tariff history to ensure 
     * price consistency.
     * 
     * @param request Payload containing target taxRateId, productIds, and categoryIds.
     * @return JSON response with the count of affected products.
     */
    @PostMapping("/apply-selective")
    @Transactional
    public ResponseEntity<?> applySelectiveTaxRate(@RequestBody ApplySelectiveTaxRateRequest request) {
        TaxRate taxRate = taxRateRepository.findById(request.getTaxRateId())
                .orElseThrow(() -> new ResourceNotFoundException("TaxRate no encontrado: " + request.getTaxRateId()));

        Set<Product> affectedProducts = new HashSet<>();

        if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            affectedProducts.addAll(productRepository.findAllById(request.getProductIds()));
        }

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            for (Long categoryId : request.getCategoryIds()) {
                affectedProducts.addAll(productRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId));
            }
        }

        if (affectedProducts.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No se seleccionaron productos ni categorías válidas o sin productos."));
        }

        List<Product> productsList = new ArrayList<>(affectedProducts);
        productService.applyTaxRateToProducts(productsList.stream().map(Product::getId).collect(Collectors.toList()), taxRate);
        
        tariffService.regenerateTariffHistoryForProducts(productsList);

        activityLogService.logActivity(
                "APLICAR_IVA_SELECTIVO",
                "IVA aplicado selectivamente: " + taxRate.getDescription() + " a " + productsList.size() + " productos.",
                "Admin",
                "TAX_RATE",
                taxRate.getId()
        );

        return ResponseEntity.ok(Map.of("success", true, "count", productsList.size()));
    }

    /**
     * Data Transfer Object for bulk tax application requests.
     */
    @Data
    public static class ApplySelectiveTaxRateRequest {
        private Long taxRateId;
        private List<Long> productIds;
        private List<Long> categoryIds;
    }
}
