package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Repository for {@link Product} entities.
 * Handles the product catalog, stock tracking, and bulk fiscal adjustments.
 * Utilizes FETCH joins to optimize performance for listing and search
 * operations.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Finds active products for the TPV interface, ordered alphabetically
     * (Spanish).
     */
    List<Product> findByActiveTrueOrderByNameEsAsc();

    /**
     * Finds a specific product by its exact Spanish name, ignoring case.
     */
    java.util.Optional<Product> findByNameEsIgnoreCase(String name);

    /**
     * Finds active products within a category, eagerly fetching associations.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit WHERE p.category.id = :categoryId AND p.active = true ORDER BY p.nameEs ASC")
    List<Product> findByCategoryIdAndActiveTrueOrderByNameEsAsc(@Param("categoryId") Long categoryId);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit WHERE (LOWER(p.nameEs) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :name, '%'))) AND p.active = true")
    List<Product> findByNameContainingIgnoreCaseAndActiveTrue(@Param("name") String name);

    /**
     * Specialized autocomplete search limited to 15 results for performance.
     */
    List<Product> findTop15ByNameEsContainingIgnoreCaseAndActiveTrue(String name);

    /**
     * Lists all active products with category and tax data in a single query.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit WHERE p.active = true ORDER BY p.nameEs ASC")
    List<Product> findAllActiveWithCategory();

    /**
     * Paginated version of active products retrieval to limit results at DB level.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit WHERE p.active = true ORDER BY p.nameEs ASC")
    List<Product> findAllActiveWithCategory(org.springframework.data.domain.Pageable pageable);

    /**
     * Lists all products (including inactive ones) with their associations.
     */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit ORDER BY p.nameEs ASC")
    List<Product> findAllWithCategory();

    /**
     * Updates the tax rate for all products matching specific old rate IDs.
     * Used when a fiscal rate (e.g., General VAT) changes globally.
     */
    @Modifying
    @Query("UPDATE Product p SET p.taxRate = :newRate WHERE p.taxRate.id IN :oldRateIds")
    void updateTaxRateForIds(@Param("oldRateIds") List<Long> oldRateIds, @Param("newRate") TaxRate newRate);

    /**
     * Recalculates the gross price (VAT included) for all products belonging to a
     * specific tax rate.
     * Required when a VAT rate is modified globally (e.g. from 21% to 23%).
     */
    @Modifying
    @Query("UPDATE Product p SET p.price = p.basePriceNet * (1 + :vatRate) WHERE p.taxRate.id = :taxRateId")
    void updateGrossPricesByTaxRate(@Param("taxRateId") Long taxRateId, @Param("vatRate") java.math.BigDecimal vatRate);

    /**
     * Finds products currently assigned to specific tax rates.
     */
    List<Product> findByTaxRateIdIn(List<Long> taxRateIds);

    /**
     * Atomic stock reduction with safety check to prevent negative inventory.
     * Returns the number of affected rows (1 if successful, 0 if insufficient stock
     * or not found).
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decreaseStockAtomic(@Param("id") Long id, @Param("quantity") java.math.BigDecimal quantity);

    /**
     * Atomic stock restoration for cancellations.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :id")
    void increaseStockAtomic(@Param("id") Long id, @Param("quantity") java.math.BigDecimal quantity);

    /**
     * Counts products whose stock level is below a given threshold.
     */
    long countByStockLessThan(java.math.BigDecimal threshold);

    /**
     * Efficiently counts products assigned to a specific category.
     * Used for safe deletion of categories without loading all product entities.
     */
    long countByCategoryId(Long categoryId);

    @Query(value = "SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit ORDER BY p.nameEs ASC", countQuery = "SELECT COUNT(p) FROM Product p")
    Page<Product> findAllWithCategoryPaged(Pageable pageable);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit WHERE p.active = true AND p.price IS NOT NULL")
    List<Product> findAllActiveForSnapshot();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit JOIN SaleLine sl ON sl.product.id = p.id WHERE p.active = true GROUP BY p.id, p.nameEs, p.nameEn, p.descriptionEs, p.descriptionEn, p.statusEs, p.statusEn, p.lowStockMessageEs, p.lowStockMessageEn, p.price, p.basePriceNet, p.stock, p.active, p.imageUrl, p.category, p.taxRate, p.measurementUnit ORDER BY SUM(sl.quantity) DESC")
    List<Product> findTopSellingProducts(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate LEFT JOIN FETCH p.measurementUnit WHERE p.active = true AND p.salesRank > 0 ORDER BY p.salesRank ASC, p.nameEs ASC")
    List<Product> findTop100BySalesRank(org.springframework.data.domain.Pageable pageable);
}