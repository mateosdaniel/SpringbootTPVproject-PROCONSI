package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.ReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link ReturnLine} entities.
 * Tracks individual items within a merchandise return.
 */
@Repository
public interface ReturnLineRepository extends JpaRepository<ReturnLine, Long> {

    /**
     * Calculates the total quantity already returned for a specific original item (SaleLine).
     * Critical for preventing "over-returns" across multiple partial refund sessions.
     * 
     * @param saleLineId ID of the original sale line.
     * @return Cumulative quantity returned (defaults to 0).
     */
    @Query("SELECT COALESCE(SUM(rl.quantity), 0) FROM ReturnLine rl WHERE rl.saleLine.id = :saleLineId")
    int sumReturnedQuantityBySaleLineId(@Param("saleLineId") Long saleLineId);
}


