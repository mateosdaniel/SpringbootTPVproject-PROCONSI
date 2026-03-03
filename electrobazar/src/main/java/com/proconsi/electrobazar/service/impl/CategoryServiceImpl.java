package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.DuplicateResourceException;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Category;
import com.proconsi.electrobazar.repository.CategoryRepository;
import com.proconsi.electrobazar.service.CategoryService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAllActive() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getFilteredCategories(String search) {
        org.springframework.data.jpa.domain.Specification<Category> spec = com.proconsi.electrobazar.repository.specification.CategorySpecification
                .filterCategories(search);
        return categoryRepository.findAll(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id: " + id));
    }

    @Override
    public Category save(Category category) {
        if (categoryRepository.existsByNameIgnoreCase(category.getName())) {
            throw new DuplicateResourceException("Ya existe una categoría con el nombre: " + category.getName());
        }
        return categoryRepository.save(category);
    }

    @Override
    public Category update(Long id, Category updated) {
        Category existing = findById(id);

        // Comprobar duplicado de nombre solo si ha cambiado
        if (!existing.getName().equalsIgnoreCase(updated.getName())
                && categoryRepository.existsByNameIgnoreCase(updated.getName())) {
            throw new DuplicateResourceException("Ya existe una categoría con el nombre: " + updated.getName());
        }

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setActive(updated.getActive());

        return categoryRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        Category category = findById(id);
        category.setActive(false);
        categoryRepository.save(category);
    }

    @Override
    public void hardDelete(Long id) {
        Category category = findById(id);
        if (!category.getProducts().isEmpty()) {
            throw new IllegalStateException("No se puede eliminar una categoría que tiene productos asociados.");
        }
        categoryRepository.delete(category);
    }
}