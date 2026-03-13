package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Product> {

    // Productos activos para el TPV
    List<Product> findByActiveTrueOrderByNameAsc();

    // Productos activos por categoría
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate WHERE p.category.id = :categoryId AND p.active = true ORDER BY p.name ASC")
    List<Product> findByCategoryIdAndActiveTrueOrderByNameAsc(@Param("categoryId") Long categoryId);

    // Buscador por nombre (contiene, ignorando mayúsculas)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.active = true")
    List<Product> findByNameContainingIgnoreCaseAndActiveTrue(@Param("name") String name);

    // Productos con su categoría en una sola query — solo activos (TPV)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate WHERE p.active = true ORDER BY p.name ASC")
    List<Product> findAllActiveWithCategory();

    // Todos los productos con su categoría — activos e inactivos (Admin)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.taxRate ORDER BY p.name ASC")
    List<Product> findAllWithCategory();

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Product p SET p.taxRate = :newRate WHERE p.taxRate.id IN :oldRateIds")
    void updateTaxRateForIds(@Param("oldRateIds") List<Long> oldRateIds, @Param("newRate") com.proconsi.electrobazar.model.TaxRate newRate);

    List<Product> findByTaxRateIdIn(List<Long> taxRateIds);

    long countByStockLessThan(Integer threshold);
}