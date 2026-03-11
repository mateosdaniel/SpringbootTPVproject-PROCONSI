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
}
