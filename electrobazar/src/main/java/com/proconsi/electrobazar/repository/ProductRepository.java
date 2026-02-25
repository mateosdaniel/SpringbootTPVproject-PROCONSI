package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Productos activos para el TPV
    List<Product> findByActiveTrueOrderByNameAsc();

    // Productos activos por categoría
    List<Product> findByCategoryIdAndActiveTrueOrderByNameAsc(Long categoryId);

    // Buscador por nombre (contiene, ignorando mayúsculas)
    List<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name);

    // Productos con su categoría en una sola query — solo activos (TPV)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.active = true ORDER BY p.name ASC")
    List<Product> findAllActiveWithCategory();

    // Todos los productos con su categoría — activos e inactivos (Admin)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category ORDER BY p.name ASC")
    List<Product> findAllWithCategory();
}