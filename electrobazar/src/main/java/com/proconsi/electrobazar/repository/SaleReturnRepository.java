package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.SaleReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

        /** All returns for a given original sale. */
        List<SaleReturn> findByOriginalSaleId(Long saleId);

        @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(r.totalRefunded), 0) FROM SaleReturn r WHERE DATE(r.createdAt) = CURRENT_DATE AND r.paymentMethod = :method")
        java.math.BigDecimal sumTotalRefundedTodayByPaymentMethod(
                        @org.springframework.data.repository.query.Param("method") com.proconsi.electrobazar.model.PaymentMethod method);

        @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(r.totalRefunded), 0) FROM SaleReturn r WHERE r.createdAt BETWEEN :from AND :to AND r.paymentMethod = :method")
        java.math.BigDecimal sumTotalRefundedBetweenByPaymentMethod(
                        @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                        @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to,
                        @org.springframework.data.repository.query.Param("method") com.proconsi.electrobazar.model.PaymentMethod method);

        /** Finds returns processed in a specific time range. */
        List<SaleReturn> findByCreatedAtBetweenOrderByCreatedAtDesc(java.time.LocalDateTime from,
                        java.time.LocalDateTime to);
}
