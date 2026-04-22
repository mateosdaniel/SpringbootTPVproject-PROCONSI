package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.SaleReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for {@link SaleReturn} entities.
 * Tracks merchandise returns and associated refunds for shift reporting.
 */
@Repository
public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long>, JpaSpecificationExecutor<SaleReturn> {

    /**
     * Retrieves all returns processed for a specific original sale.
     */
    List<SaleReturn> findByOriginalSaleId(Long saleId);

    /**
     * Calculates the total amount refunded today for a specific payment method.
     */
    @Query("SELECT COALESCE(SUM(r.totalRefunded), 0) FROM SaleReturn r WHERE DATE(r.createdAt) = CURRENT_DATE AND r.paymentMethod = :method")
    BigDecimal sumTotalRefundedTodayByPaymentMethod(@Param("method") PaymentMethod method);

    /**
     * Calculates the total amount refunded in a given interval for a specific payment method.
     */
    @Query("SELECT COALESCE(SUM(r.totalRefunded), 0) FROM SaleReturn r WHERE r.createdAt BETWEEN :from AND :to AND r.paymentMethod = :method")
    BigDecimal sumTotalRefundedBetweenByPaymentMethod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("method") PaymentMethod method);

    /**
     * Counts returns processed within a specific time range.
     */
    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Lists returns processed within a specific time range with deep fetch joins for optimization.
     * Fetches original sale, its linked documents (invoice/ticket), and return lines with product info.
     */
    @Query("SELECT DISTINCT r FROM SaleReturn r " +
           "LEFT JOIN FETCH r.lines rl " +
           "LEFT JOIN FETCH rl.saleLine sl " +
           "LEFT JOIN FETCH r.originalSale s " +
           "LEFT JOIN FETCH s.ticket " +
           "LEFT JOIN FETCH s.invoice " +
           "WHERE r.createdAt BETWEEN :from AND :to " +
           "ORDER BY r.createdAt DESC")
    List<SaleReturn> findByCreatedAtBetweenWithDetails(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
