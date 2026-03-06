package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "lines", "lines.product", "customer",
                        "worker" })
        @Query("SELECT s FROM Sale s ORDER BY s.createdAt DESC")
        List<Sale> findAllWithDetails();

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "lines", "lines.product", "customer",
                        "worker" })
        java.util.Optional<Sale> findById(Long id);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "lines", "lines.product", "customer",
                        "worker" })
        List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

        // Ventas de hoy
        @Query("SELECT s FROM Sale s WHERE DATE(s.createdAt) = CURRENT_DATE ORDER BY s.createdAt DESC")
        List<Sale> findToday();

        // Total recaudado en un rango de fechas
        @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
        BigDecimal sumTotalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

        // Total por método de pago en un rango de fechas
        @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.paymentMethod = :method AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
        Optional<BigDecimal> sumTotalBetweenByPaymentMethod(@Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to, @Param("method") PaymentMethod method);

        // Número de ventas hoy
        @Query("SELECT COUNT(s) FROM Sale s WHERE DATE(s.createdAt) = CURRENT_DATE AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
        long countToday();

        // Contar ventas en un rango de fechas
        @Query("SELECT COUNT(s) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
        long countByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

        // Resumen de ventas (proyección optimizada)
        @Query("SELECT new com.proconsi.electrobazar.dto.SaleSummaryResponse(" +
                        "COUNT(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE THEN 1 END), "
                        +
                        "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE THEN s.totalAmount ELSE 0 END), 0), "
                        +
                        "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE AND s.paymentMethod = com.proconsi.electrobazar.model.PaymentMethod.CASH THEN s.totalAmount ELSE 0 END), 0), "
                        +
                        "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE AND s.paymentMethod = com.proconsi.electrobazar.model.PaymentMethod.CARD THEN s.totalAmount ELSE 0 END), 0), "
                        +
                        "COUNT(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.CANCELLED THEN 1 END), "
                        +
                        "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.CANCELLED THEN s.totalAmount ELSE 0 END), 0)) "
                        +
                        "FROM Sale s WHERE s.createdAt BETWEEN :from AND :to")
        com.proconsi.electrobazar.dto.SaleSummaryResponse getSummaryBetween(@Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);
}