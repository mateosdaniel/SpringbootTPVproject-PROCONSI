package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Sale} entities.
 * Central hub for sales data, reporting, and dashboard statistics.
 * extensively uses EntityGraphs and custom JPQL projections for performance.
 */
@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    /**
     * Retrieves all sales with associated lines, products, customers, and workers in one trip.
     */
    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker" })
    @Query("SELECT s FROM Sale s ORDER BY s.createdAt DESC")
    List<Sale> findAllWithDetails();

    /**
     * Eagerly fetches a specific sale by ID.
     */
    @Override
    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker" })
    Optional<Sale> findById(Long id);

    /**
     * Finds sales within a specific time range, eagerly loading associations.
     */
    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker" })
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    /**
     * Lists sales processed on the current calendar day.
     */
    @Query("SELECT s FROM Sale s WHERE DATE(s.createdAt) = CURRENT_DATE ORDER BY s.createdAt DESC")
    List<Sale> findToday();

    /**
     * Calculates the total revenue from active sales between two points in time.
     */
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
    BigDecimal sumTotalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Calculates the total revenue for a specific payment method in a given interval.
     */
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.paymentMethod = :method AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
    Optional<BigDecimal> sumTotalBetweenByPaymentMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("method") PaymentMethod method);

    /**
     * Counts the number of active sales recorded today.
     */
    @Query("SELECT COUNT(s) FROM Sale s WHERE DATE(s.createdAt) = CURRENT_DATE AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
    long countToday();

    /**
     * Counts the number of active sales within a given time range.
     */
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE")
    long countByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Aggregates sales data into a summary DTO.
     * Computes totals for active, cancelled, cash, and card sales in a single database pass.
     */
    @Query("SELECT new com.proconsi.electrobazar.dto.SaleSummaryResponse(" +
            "COUNT(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE THEN 1 END), " +
            "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE THEN s.totalAmount ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE AND s.paymentMethod = com.proconsi.electrobazar.model.PaymentMethod.CASH THEN s.totalAmount ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE AND s.paymentMethod = com.proconsi.electrobazar.model.PaymentMethod.CARD THEN s.totalAmount ELSE 0 END), 0), " +
            "COUNT(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.CANCELLED THEN 1 END), " +
            "COALESCE(SUM(CASE WHEN s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.CANCELLED THEN s.totalAmount ELSE 0 END), 0)) " +
            "FROM Sale s WHERE s.createdAt BETWEEN :from AND :to")
    SaleSummaryResponse getSummaryBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Identifies the name of the top-selling product in a given interval.
     */
    @Query(value = "SELECT p.name FROM sales s JOIN sale_lines sl ON s.id = sl.sale_id JOIN products p ON sl.product_id = p.id WHERE s.created_at BETWEEN :from AND :to AND s.status = 'ACTIVE' GROUP BY p.name ORDER BY SUM(sl.quantity) DESC LIMIT 1", nativeQuery = true)
    String findTopProductNameBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Lists all sales for a specific customer, ordered by date.
     */
    @EntityGraph(attributePaths = { "lines", "lines.product", "worker" })
    @Query("SELECT s FROM Sale s WHERE s.customer.id = :customerId ORDER BY s.createdAt DESC")
    List<Sale> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);
}