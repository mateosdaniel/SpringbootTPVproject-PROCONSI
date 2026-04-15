package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.TariffPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Repository for {@link TariffPriceHistory} entities.
 * Provides time-series access to product prices under specific tariff contexts.
 */
@Repository
public interface TariffPriceHistoryRepository extends JpaRepository<TariffPriceHistory, Long> {

        /**
         * Retrieves the chronological price history (most recent first) for a specific
         * product.
         */
        List<TariffPriceHistory> findByProductIdOrderByValidFromDesc(Long productId);

        /**
         * Retrieves all price histories associated with a specific tariff.
         */
        List<TariffPriceHistory> findByTariffIdOrderByValidFromDesc(Long tariffId);

        boolean existsByTariffId(Long tariffId);

        @Query("SELECT t FROM TariffPriceHistory t WHERE t.product.id = :productId AND t.tariff.id = :tariffId AND t.validTo IS NULL")
        Optional<TariffPriceHistory> findCurrentByProductAndTariff(@Param("productId") Long productId,
                        @Param("tariffId") Long tariffId);

        /**
         * Efficiently retrieves active price history records for many products in bulk.
         * Prevents N+1 query patterns during mass price updates.
         */
        @Query("SELECT t FROM TariffPriceHistory t WHERE t.product.id IN :productIds AND t.validTo IS NULL")
        List<TariffPriceHistory> findAllCurrentByProductIds(@Param("productIds") List<Long> productIds);

        /**
         * Lists distinct dates (days) when historical price transitions occurred for a
         * tariff.
         */
        @Query("SELECT DISTINCT CAST(t.validFrom AS date) FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId ORDER BY 1 DESC")
        List<java.sql.Date> findDistinctValidFromByTariffId(@Param("tariffId") Long tariffId);

        /**
         * Finds price records that were active at a specific point in time for a given
         * tariff.
         */
        @Query("SELECT t FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId AND t.validFrom <= :dateTime AND (t.validTo > :dateTime OR t.validTo IS NULL)")
        Page<TariffPriceHistory> findByTariffIdAndDateTime(@Param("tariffId") Long tariffId,
                        @Param("dateTime") LocalDateTime dateTime, Pageable pageable);

        @Query("SELECT t FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId AND t.validFrom <= :dateTime AND (t.validTo > :dateTime OR t.validTo IS NULL)")
        List<TariffPriceHistory> findAllByTariffIdAndDateTime(@Param("tariffId") Long tariffId,
                        @Param("dateTime") LocalDateTime dateTime);

        /**
         * Lists distinct version start times for a given tariff and day range.
         */
        @Query("SELECT DISTINCT t.validFrom FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId AND t.validFrom >= :startOfDay AND t.validFrom < :startOfNextDay ORDER BY t.validFrom ASC")
        List<LocalDateTime> findVersionsForTariffAndDayRange(@Param("tariffId") Long tariffId,
                        @Param("startOfDay") LocalDateTime startOfDay,
                        @Param("startOfNextDay") LocalDateTime startOfNextDay);

        /**
         * Finds price records that started exactly at a specific timestamp for a tariff
         * (Paginated).
         */
        @Query("SELECT t FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId AND t.validFrom = :validFrom")
        Page<TariffPriceHistory> findByTariffIdAndValidFrom(@Param("tariffId") Long tariffId,
                        @Param("validFrom") LocalDateTime validFrom, Pageable pageable);

        /**
         * Finds price records that started exactly at a specific timestamp for a tariff
         * (List).
         */
        List<TariffPriceHistory> findByTariffIdAndValidFrom(@Param("tariffId") Long tariffId,
                        @Param("validFrom") LocalDateTime validFrom);

        /**
         * Updates the validFrom timestamp for all records of a tariff that share a
         * specific old timestamp.
         * Used to group slow batch snapshots into a single version.
         */
        @Modifying
        @Query("UPDATE TariffPriceHistory t SET t.validFrom = :newTime WHERE t.tariff.id = :tariffId AND t.validFrom = :oldTime")
        void updateValidFromForTariffAndTime(@Param("tariffId") Long tariffId, @Param("oldTime") LocalDateTime oldTime,
                        @Param("newTime") LocalDateTime newTime);
}
