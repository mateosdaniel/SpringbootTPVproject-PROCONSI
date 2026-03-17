package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

/**
 * REST Controller for managing product categories.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryApiRestController {

    private final CategoryService categoryService;

    /**
     * Retrieves all active categories.
     * @return List of {@link Category} entities.
     */
    @GetMapping
    public ResponseEntity<List<Category>> getAll() {
        return ResponseEntity.ok(categoryService.findAllActive());
    }

    /**
     * Filters categories by name.
     * @param search Search query string.
     * @return List of filtered categories.
     */
    @GetMapping("/filter")
    public ResponseEntity<List<Category>> filterCategories(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(categoryService.getFilteredCategories(search));
    }

    /**
     * Retrieves a single category by its ID.
     * @param id Category ID.
     * @return The {@link Category} entity.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Category> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    /**
     * Creates a new category.
     * @param category Category details.
     * @return 201 Created with the saved entity.
     */
    @PostMapping
    public ResponseEntity<Category> create(@Valid @RequestBody Category category) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.save(category));
    }

    /**
     * Updates an existing category.
     * @param id Category ID to update.
     * @param category New details.
     * @return 200 OK with the updated entity.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Category> update(@PathVariable Long id, @Valid @RequestBody Category category) {
        return ResponseEntity.ok(categoryService.update(id, category));
    }

    /**
     * Deactivates a category (Soft Delete).
     * @param id Category ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}