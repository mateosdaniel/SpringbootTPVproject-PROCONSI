package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.ReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReturnLineRepository extends JpaRepository<ReturnLine, Long> {

    /**
     * Sums all quantities already returned for a specific original SaleLine.
     * Used during validation to ensure we don't exceed original quantity.
     */
    @Query("SELECT COALESCE(SUM(rl.quantity), 0) FROM ReturnLine rl WHERE rl.saleLine.id = :saleLineId")
    int sumReturnedQuantityBySaleLineId(@Param("saleLineId") Long saleLineId);
}
