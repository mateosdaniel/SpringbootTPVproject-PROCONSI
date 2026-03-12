package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.TaxRate;
import com.proconsi.electrobazar.repository.TaxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api/tax-rates")
@RequiredArgsConstructor
public class TaxRateApiRestController {

    private final TaxRateRepository taxRateRepository;
    private final com.proconsi.electrobazar.service.ActivityLogService activityLogService;
    private final com.proconsi.electrobazar.service.ProductService productService;
    private final com.proconsi.electrobazar.service.TariffService tariffService;
    private final com.proconsi.electrobazar.repository.ProductRepository productRepository;

    @GetMapping
    public List<TaxRate> getAll() {
        return taxRateRepository.findAll();
    }

    @GetMapping("/active")
    public List<TaxRate> getActive() {
        return taxRateRepository.findByActiveTrue();
    }

    @PostMapping
    public TaxRate create(@RequestBody TaxRate taxRate) {
        // Automatically set valid_to of current active TaxRate with same description
        if (taxRate.getValidFrom() != null) {
            taxRateRepository.findByActiveTrue().stream()
                .filter(tr -> tr.getDescription() != null && tr.getDescription().equals(taxRate.getDescription()))
                .forEach(tr -> {
                    tr.setValidTo(taxRate.getValidFrom().minusDays(1));
                    taxRateRepository.save(tr);
                });
        }
        TaxRate saved = taxRateRepository.save(taxRate);
        activityLogService.logActivity("CREAR_IVA", "Nuevo tipo de IVA creado: " + saved.getDescription() + " (" + saved.getVatRate().multiply(new java.math.BigDecimal("100")) + "%)", "Admin", "TAX_RATE", saved.getId());
        return saved;
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaxRate> update(@PathVariable Long id, @RequestBody TaxRate taxRate) {
        return taxRateRepository.findById(id).map(existing -> {
            existing.setVatRate(taxRate.getVatRate());
            existing.setReRate(taxRate.getReRate());
            existing.setDescription(taxRate.getDescription());
            existing.setActive(taxRate.getActive());
            existing.setValidFrom(taxRate.getValidFrom());
            existing.setValidTo(taxRate.getValidTo());

            // If we are updating validFrom, we might need to adjust the previous rate's validTo
            if (taxRate.getValidFrom() != null) {
                taxRateRepository.findByActiveTrue().stream()
                    .filter(tr -> !tr.getId().equals(id) && tr.getDescription() != null && tr.getDescription().equals(taxRate.getDescription()))
                    .forEach(tr -> {
                        tr.setValidTo(taxRate.getValidFrom().minusDays(1));
                        taxRateRepository.save(tr);
                    });
            }

            TaxRate saved = taxRateRepository.save(existing);
            activityLogService.logActivity("ACTUALIZAR_IVA", "Tipo de IVA actualizado: " + saved.getDescription() + " (" + saved.getVatRate().multiply(new java.math.BigDecimal("100")) + "%)", "Admin", "TAX_RATE", saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taxRateRepository.findById(id).ifPresent(tr -> {
            taxRateRepository.deleteById(id);
            activityLogService.logActivity("ELIMINAR_IVA", "Tipo de IVA eliminado: " + tr.getDescription(), "Admin", "TAX_RATE", id);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/apply-selective")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> applySelectiveTaxRate(@RequestBody ApplySelectiveTaxRateRequest request) {
        com.proconsi.electrobazar.model.TaxRate taxRate = taxRateRepository.findById(request.getTaxRateId())
                .orElseThrow(() -> new com.proconsi.electrobazar.exception.ResourceNotFoundException("TaxRate no encontrado: " + request.getTaxRateId()));

        java.util.Set<com.proconsi.electrobazar.model.Product> affectedProducts = new java.util.HashSet<>();

        if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            affectedProducts.addAll(productRepository.findAllById(request.getProductIds()));
        }

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            for (Long categoryId : request.getCategoryIds()) {
                affectedProducts.addAll(productRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId));
            }
        }

        if (affectedProducts.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "No se seleccionaron productos ni categorías válidas o sin productos."));
        }

        java.util.List<com.proconsi.electrobazar.model.Product> productsList = new java.util.ArrayList<>(affectedProducts);
        productService.applyTaxRateToProducts(productsList.stream().map(com.proconsi.electrobazar.model.Product::getId).collect(java.util.stream.Collectors.toList()), taxRate);
        
        tariffService.regenerateTariffHistoryForProducts(productsList);

        activityLogService.logActivity(
                "APLICAR_IVA_SELECTIVO",
                "IVA aplicado selectivamente: " + taxRate.getDescription() + " a " + productsList.size() + " productos.",
                "Admin",
                "TAX_RATE",
                taxRate.getId()
        );

        return ResponseEntity.ok(java.util.Map.of("success", true, "count", productsList.size()));
    }

    @lombok.Data
    public static class ApplySelectiveTaxRateRequest {
        private Long taxRateId;
        private java.util.List<Long> productIds;
        private java.util.List<Long> categoryIds;
    }
}
