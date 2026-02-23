package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Category;
import java.util.List;

public interface CategoryService {
    List<Category> findAll();
    List<Category> findAllActive();
    Category findById(Long id);
    Category save(Category category);
    Category update(Long id, Category category);
    void delete(Long id);         // soft delete
    void hardDelete(Long id);     // borrado real (solo si no tiene productos)
}