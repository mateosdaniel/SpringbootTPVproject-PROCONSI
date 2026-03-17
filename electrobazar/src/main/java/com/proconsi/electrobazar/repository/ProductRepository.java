package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Product} entities.
 * Handles the product catalog, stock tracking, and bulk fiscal adjustments.
 * Utilizes FETCH joins to optimize performance for listing and search operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Finds active products for the TPV interface, ordered alphabetically.
     */
    List<Product> findByActiveTrueOrderByNameAsc();

    /**
     * Finds active products within a category, eagerly fetching associations.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate WHERE p.category.id = :categoryId AND p.active = true ORDER BY p.name ASC")
    List<Product> findByCategoryIdAndActiveTrueOrderByNameAsc(@Param("categoryId") Long categoryId);

    /**
     * Generic search for active products by name.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.active = true")
    List<Product> findByNameContainingIgnoreCaseAndActiveTrue(@Param("name") String name);

    /**
     * Lists all active products with category and tax data in a single query.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate WHERE p.active = true ORDER BY p.name ASC")
    List<Product> findAllActiveWithCategory();

    /**
     * Lists all products (including inactive ones) with their associations.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate ORDER BY p.name ASC")
    List<Product> findAllWithCategory();

    /**
     * Updates the tax rate for all products matching specific old rate IDs.
     * Used when a fiscal rate (e.g., General VAT) changes globally.
     */
    @Modifying
    @Query("UPDATE Product p SET p.taxRate = :newRate WHERE p.taxRate.id IN :oldRateIds")
    void updateTaxRateForIds(@Param("oldRateIds") List<Long> oldRateIds, @Param("newRate") TaxRate newRate);

    /**
     * Finds products currently assigned to specific tax rates.
     */
    List<Product> findByTaxRateIdIn(List<Long> taxRateIds);

    /**
     * Counts products whose stock level is below a given threshold.
     */
    long countByStockLessThan(Integer threshold);
}