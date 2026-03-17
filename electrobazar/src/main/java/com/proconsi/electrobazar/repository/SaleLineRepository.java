package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.SaleLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link SaleLine} entities.
 * Manages individual product entries within a ticket or invoice.
 */
@Repository
public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    /**
     * Retrieves all items associated with a specific sale.
     */
    List<SaleLine> findBySaleId(Long saleId);

    /**
     * Counts the total number of times a product has been sold (transaction count).
     * Useful for ranking top-selling products.
     */
    long countByProductId(Long productId);
}