package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Category} entities.
 * Handles product classification and supports advanced filtering via JPA Specifications.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    /**
     * Checks if a category with a given name exists, ignoring case sensitivity.
     * Useful for validation during creation/editing.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Lists all active categories for display in the TPV interface.
     */
    List<Category> findByActiveTrueOrderByNameAsc();

    /**
     * Finds a category by its exact name, ignoring case.
     */
    Optional<Category> findByNameIgnoreCase(String name);
}