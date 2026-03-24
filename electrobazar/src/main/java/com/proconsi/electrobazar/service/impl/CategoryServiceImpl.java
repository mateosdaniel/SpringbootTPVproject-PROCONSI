package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.DuplicateResourceException;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.repository.CategoryRepository;
import com.proconsi.electrobazar.repository.specification.CategorySpecification;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CategoryService;
import com.proconsi.electrobazar.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link CategoryService}.
 * Provides standard CRUD and filtering logic using JPA Specifications.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ActivityLogService activityLogService;
    private final TranslationService translationService;

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAllActive() {
        return categoryRepository.findByActiveTrueOrderByNameEsAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getFilteredCategories(String search) {
        Specification<Category> spec = CategorySpecification.filterCategories(search);
        return categoryRepository.findAll(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    @Override
    public Category save(Category category) {
        if (categoryRepository.existsByNameEsIgnoreCase(category.getName())) {
            throw new DuplicateResourceException("A category with name '" + category.getName() + "' already exists.");
        }
        autoTranslateCategory(category);
        Category saved = categoryRepository.save(category);
        activityLogService.logActivity(
                "CREAR_CATEGORIA",
                "New category created: " + saved.getName(),
                "Admin",
                "CATEGORY",
                saved.getId());
        return saved;
    }

    @Override
    public Category update(Long id, Category updated) {
        Category existing = findById(id);

        if (!existing.getNameEs().equalsIgnoreCase(updated.getName())
                && categoryRepository.existsByNameEsIgnoreCase(updated.getName())) {
            throw new DuplicateResourceException("A category with name '" + updated.getName() + "' already exists.");
        }

        existing.setNameEs(updated.getName());
        existing.setDescriptionEs(updated.getDescription());
        existing.setActive(updated.getActive());
        
        autoTranslateCategory(existing);

        Category saved = categoryRepository.save(existing);
        activityLogService.logActivity(
                "ACTUALIZAR_CATEGORIA",
                "Category updated: " + saved.getName(),
                "Admin",
                "CATEGORY",
                saved.getId());
        return saved;
    }

    private void autoTranslateCategory(Category category) {
        if (category.getNameEs() == null || category.getNameEs().isBlank()) return;

        TranslationService.TranslationResult nameResult = translationService.translateWithDetection(category.getNameEs(), "EN");
        String detected = nameResult.detectedLanguage();

        if (detected != null) {
            if (detected.equalsIgnoreCase("ES")) {
                category.setNameEn(nameResult.text());
                category.setDescriptionEn(translationService.translate(category.getDescriptionEs(), "EN"));
            } else if (detected.toUpperCase().startsWith("EN")) {
                category.setNameEn(category.getNameEs());
                category.setDescriptionEn(category.getDescriptionEs());

                category.setNameEs(translationService.translate(category.getNameEn(), "ES"));
                category.setDescriptionEs(translationService.translate(category.getDescriptionEn(), "ES"));
            }
        }
    }


    @Override
    public void delete(Long id) {
        Category category = findById(id);
        category.setActive(false);
        categoryRepository.save(category);

        activityLogService.logActivity(
                "DESACTIVAR_CATEGORIA",
                "Category deactivated: " + category.getName(),
                "Admin",
                "CATEGORY",
                category.getId());
    }

    @Override
    public void hardDelete(Long id) {
        Category category = findById(id);
        if (category.getProducts() != null && !category.getProducts().isEmpty()) {
            throw new IllegalStateException("Cannot delete a category that still contains products.");
        }
        categoryRepository.delete(category);

        activityLogService.logActivity(
                "ELIMINAR_CATEGORIA_HARD",
                "Category permanently deleted: " + category.getName(),
                "Admin",
                "CATEGORY",
                id);
    }
}