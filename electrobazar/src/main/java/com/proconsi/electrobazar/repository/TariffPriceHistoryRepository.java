package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.TariffPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TariffPriceHistoryRepository extends JpaRepository<TariffPriceHistory, Long> {

    List<TariffPriceHistory> findByProductIdOrderByValidFromDesc(Long productId);

    List<TariffPriceHistory> findByTariffIdOrderByValidFromDesc(Long tariffId);

    @Query("SELECT t FROM TariffPriceHistory t WHERE t.product.id = :productId AND t.tariff.id = :tariffId AND t.validTo IS NULL")
    Optional<TariffPriceHistory> findCurrentByProductAndTariff(@Param("productId") Long productId,
            @Param("tariffId") Long tariffId);

    @Query("SELECT DISTINCT t.validFrom FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId ORDER BY t.validFrom DESC")
    List<java.time.LocalDate> findDistinctValidFromByTariffId(@Param("tariffId") Long tariffId);

    @Query("SELECT t FROM TariffPriceHistory t WHERE t.tariff.id = :tariffId AND t.validFrom <= :date AND (t.validTo >= :date OR t.validTo IS NULL)")
    List<TariffPriceHistory> findByTariffIdAndDate(@Param("tariffId") Long tariffId, @Param("date") java.time.LocalDate date);

    List<TariffPriceHistory> findByTariffIdAndValidFrom(@Param("tariffId") Long tariffId, @Param("validFrom") java.time.LocalDate validFrom);
}
