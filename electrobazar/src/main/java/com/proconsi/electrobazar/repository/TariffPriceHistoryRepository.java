package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.TariffPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link TariffPriceHistory} entities.
 * Provides time-series access to product prices under specific tariff contexts.
 */
@Repository
public interface TariffPriceHistoryRepository extends JpaRepository<TariffPriceHistory, Long> {

    /**
     * Retrieves the chronological price history (most recent first) for a specific product.
     */
    List<TariffPriceHistory> findByProductIdOrderByValidFromDesc(Long productId);

    /**
     * Retrieves all price histories associated with a specific tariff.
     */
    List<TariffPriceHistory> findByTariffIdOrderByValidFromDesc(Long tariffId);

    /**
     * Finds the currently active record (no validTo date) for a product+tariff pair.
     */
    @Query("SELECT t FROM TariffPriceHistory t WHERE t.product.id = :productId AND t.tariff.id = :tariffId AND t.validTo IS NULL")
    Optional<TariffPriceHistory> findCurrentByProductAndTariff(@Param("productId") Long productId, @Param("tariffId") Long tariffId);

    /**
     * Lists distinct dates when historical price transitions occurred for a tariff.
     */
    @Query("SELECT DISTINCT t.validFrom FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId ORDER BY t.validFrom DESC")
    List<LocalDate> findDistinctValidFromByTariffId(@Param("tariffId") Long tariffId);

    /**
     * Finds price records that were active at a specific point in time for a given tariff.
     */
    @Query("SELECT t FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId AND t.validFrom <= :date AND (t.validTo >= :date OR t.validTo IS NULL)")
    List<TariffPriceHistory> findByTariffIdAndDate(@Param("tariffId") Long tariffId, @Param("date") LocalDate date);

    /**
     * Finds price records that started exactly on a specific date for a tariff.
     */
    List<TariffPriceHistory> findByTariffIdAndValidFrom(@Param("tariffId") Long tariffId, @Param("validFrom") LocalDate validFrom);
}


