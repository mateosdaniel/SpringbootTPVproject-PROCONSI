package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    /**
     * Fetches the active price for a given product at a specific point in time.
     * A price is considered active when:
     *   - The given timestamp is on or after the startDate, AND
     *   - The given timestamp is on or before the endDate, OR the endDate is null (open-ended).
     *
     * @param productId the ID of the product
     * @param now       the timestamp to evaluate against
     * @return an Optional containing the active ProductPrice, or empty if none found
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
     * Fetches all price records for a given product, ordered by startDate descending.
     *
     * @param productId the ID of the product
     * @return list of all ProductPrice records for the product
     */
    @Query("""
            SELECT p FROM ProductPrice p
            WHERE p.product.id = :productId
            ORDER BY p.startDate DESC
            """)
    List<ProductPrice> findAllByProductId(@Param("productId") Long productId);

    /**
     * Finds the currently open-ended price (endDate IS NULL) for a product.
     * There should be at most one such record per product at any time.
     *
     * @param productId the ID of the product
     * @return an Optional containing the open-ended ProductPrice, or empty if none
     */
    @Query("""
            SELECT p FROM ProductPrice p
            WHERE p.product.id = :productId
              AND p.endDate IS NULL
            ORDER BY p.startDate DESC
            """)
    Optional<ProductPrice> findCurrentOpenPrice(@Param("productId") Long productId);

    /**
     * Finds all price records whose startDate is in the future (scheduled prices).
     * Useful for the daily scheduler to verify upcoming price transitions.
     *
     * @param now the current timestamp
     * @return list of future-scheduled ProductPrice records
     */
    @Query("""
            SELECT p FROM ProductPrice p
            WHERE p.startDate > :now
            ORDER BY p.startDate ASC
            """)
    List<ProductPrice> findAllFuturePrices(@Param("now") LocalDateTime now);

    /**
     * Finds all price records that have transitioned today (startDate is between
     * the start and end of the given day). Used by the daily scheduler for logging.
     *
     * @param dayStart start of the day
     * @param dayEnd   end of the day
     * @return list of ProductPrice records that became active today
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
