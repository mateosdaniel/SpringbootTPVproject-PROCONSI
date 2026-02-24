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

    // Ventas en un rango de fechas (para informes / cierre de caja)
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    // Ventas de hoy
    @Query("SELECT s FROM Sale s WHERE DATE(s.createdAt) = CURRENT_DATE ORDER BY s.createdAt DESC")
    List<Sale> findToday();

    // Total recaudado en un rango de fechas
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to")
    BigDecimal sumTotalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // Total por método de pago en un rango de fechas
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.paymentMethod = :method")
    Optional<BigDecimal> sumTotalBetweenByPaymentMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("method") PaymentMethod method);

    // Número de ventas hoy
    @Query("SELECT COUNT(s) FROM Sale s WHERE DATE(s.createdAt) = CURRENT_DATE")
    long countToday();

    // Contar ventas en un rango de fechas
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to")
    long countByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}