package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.service.TariffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing custom Tariffs.
 * Tariffs allow applying specific discount percentages to products based on customer levels.
 */
@RestController
@RequestMapping("/api/tariffs")
@RequiredArgsConstructor
public class TariffApiRestController {

    private final TariffService tariffService;

    /**
     * Retrieves all active tariffs.
     * @param includeInactive If true, returns deactivated tariffs as well.
     * @return List of {@link Tariff} entities.
     */
    @GetMapping
    public ResponseEntity<List<Tariff>> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        List<Tariff> tariffs = includeInactive ? tariffService.findAll() : tariffService.findAllActive();
        return ResponseEntity.ok(tariffs);
    }

    /**
     * Retrieves a tariff by its ID.
     * @param id Tariff ID.
     * @return The requested {@link Tariff}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return tariffService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Statistics: returns the count of customers assigned to each tariff.
     * @return Map of Tariff ID to customer count.
     */
    @GetMapping("/customer-counts")
    public ResponseEntity<Map<Long, Long>> getCustomerCounts() {
        return ResponseEntity.ok(tariffService.getCustomerCountPerTariff());
    }

    /**
     * Creates a new custom tariff.
     * @param body Map containing 'name', 'discountPercentage', and optional 'description'.
     * @return 201 Created with the new {@link Tariff}.
     */
    @PostMapping
    public ResponseEntity<Tariff> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        BigDecimal discount = new BigDecimal(body.get("discountPercentage").toString());
        String description = (String) body.getOrDefault("description", "");
        Tariff created = tariffService.create(name, discount, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing custom tariff.
     * @param id Tariff ID.
     * @param body Map with updated 'discountPercentage' and 'description'.
     * @return 200 OK with the updated {@link Tariff}.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Tariff> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal discount = new BigDecimal(body.get("discountPercentage").toString());
        String description = (String) body.getOrDefault("description", "");
        Tariff updated = tariffService.update(id, discount, description);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deactivates a tariff, preventing its selection for new customers.
     * @param id Tariff ID.
     * @return Success message.
     */
    @DeleteMapping("/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivate(@PathVariable Long id) {
        tariffService.deactivate(id);
        return ResponseEntity.ok(Map.of("message", "Tarifa desactivada correctamente."));
    }

    /**
     * Re-activates a previously deactivated tariff.
     * @param id Tariff ID.
     * @return Success message.
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, String>> activate(@PathVariable Long id) {
        tariffService.activate(id);
        return ResponseEntity.ok(Map.of("message", "Tarifa activada correctamente."));
    }
}
