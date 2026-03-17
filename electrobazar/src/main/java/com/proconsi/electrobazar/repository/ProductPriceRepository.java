package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ProductPrice} entities.
 * Manages chronological price history and scheduling for products.
 */
@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    /**
     * Fetches the active price for a product at a specific timestamp.
     * A record is active if [now] is between its startDate and endDate (or endDate is null).
     */
    @Query("""
            SELECT p FROM ProductPrice p
            WHERE p.product.id = :productId
              AND :now >= p.startDate
              AND (:now <= p.endDate OR p.endDate IS NULL)
            ORDER BY p.startDate DESC
            """)
    Optional<ProductPrice> findActivePriceAt(
            @Param("productId") Long productId,
            @Param("now") LocalDateTime now);

    /**
     * Retrieves all historical and scheduled price entries for a product.
     */
    @Query("SELECT p FROM ProductPrice p WHERE p.product.id = :productId ORDER BY p.startDate DESC")
    List<ProductPrice> findAllByProductId(@Param("productId") Long productId);

    /**
     * Finds the current open-ended price entry (endDate is null).
     * There should only be one such entry per product at any given time.
     */
    @Query("SELECT p FROM ProductPrice p WHERE p.product.id = :productId AND p.endDate IS NULL")
    Optional<ProductPrice> findCurrentOpenPrice(@Param("productId") Long productId);

    /**
     * Lists all prices scheduled to take effect in the future.
     */
    @Query("SELECT p FROM ProductPrice p WHERE p.startDate > :now ORDER BY p.startDate ASC")
    List<ProductPrice> findAllFuturePrices(@Param("now") LocalDateTime now);

    /**
     * Lists prices that transitioned (started) within a specific time window.
     */
    @Query("""
            SELECT p FROM ProductPrice p
            WHERE p.startDate >= :dayStart
              AND p.startDate <= :dayEnd
            ORDER BY p.startDate ASC
            """)
    List<ProductPrice> findPricesActivatedBetween(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);
}


