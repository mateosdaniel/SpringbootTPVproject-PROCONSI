package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.dto.SaleSummaryProjection;
import com.proconsi.electrobazar.dto.WorkerSaleStatsDTO;


import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Sale} entities.
 * Central hub for sales data, reporting, and dashboard statistics.
 */
@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    @EntityGraph(attributePaths = { "customer", "worker", "invoice", "ticket", "coupon" })
    @Query("SELECT s FROM Sale s")
    Page<Sale> findAll(Pageable pageable);

    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findWithDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    @Query("SELECT s FROM Sale s ORDER BY s.createdAt DESC")
    List<Sale> findAllWithDetails();

    @Override
    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    Optional<Sale> findById(Long id);

    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    @Query(value = "SELECT * FROM sales WHERE created_at >= CURDATE() AND status = 'ACTIVE' ORDER BY created_at DESC", nativeQuery = true)
    List<Sale> findToday();


    @Query(value = "SELECT COALESCE(SUM(total_amount), 0) FROM sales WHERE created_at BETWEEN :from AND :to AND status = 'ACTIVE'", nativeQuery = true)
    BigDecimal sumTotalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT COALESCE(SUM(total_amount), 0) FROM sales WHERE created_at BETWEEN :from AND :to AND payment_method = :method AND status = 'ACTIVE'", nativeQuery = true)
    BigDecimal sumTotalBetweenByPaymentMethodNative(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("method") String method);

    // Keep compatibility with existing code using PaymentMethod enum
    default Optional<BigDecimal> sumTotalBetweenByPaymentMethod(LocalDateTime from, LocalDateTime to, PaymentMethod method) {
        return Optional.ofNullable(sumTotalBetweenByPaymentMethodNative(from, to, method.name()));
    }

    @Query(value = "SELECT COUNT(*) FROM sales WHERE created_at >= CURDATE() AND status = 'ACTIVE'", nativeQuery = true)
    long countToday();


    @Query(value = "SELECT COUNT(*) FROM sales WHERE created_at BETWEEN :from AND :to AND status = 'ACTIVE'", nativeQuery = true)
    long countByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);


    /**
     * Returns aggregated KPIs using a single native SQL pass over the sales table.
     * Uses the composite index (created_at, status) for range filtering.
     * MUCH faster than the JPQL CASE WHEN version which Hibernate can mis-optimize.
     */
    @Query(value = """
            SELECT
                COUNT(CASE WHEN status = 'ACTIVE' THEN 1 END)                                  AS totalSalesCount,
                COALESCE(SUM(CASE WHEN status = 'ACTIVE' THEN total_amount ELSE 0 END), 0)     AS totalSalesAmount,
                COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND payment_method = 'CASH'  THEN total_amount ELSE 0 END), 0) AS totalCashAmount,
                COALESCE(SUM(CASE WHEN status = 'ACTIVE' AND payment_method = 'CARD'  THEN total_amount ELSE 0 END), 0) AS totalCardAmount,
                COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END)                               AS totalCancelledCount,
                COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN total_amount ELSE 0 END), 0)  AS totalCancelledAmount
            FROM sales
            WHERE created_at BETWEEN :from AND :to
            """, nativeQuery = true)
    SaleSummaryProjection getSummaryBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);


    @Query(value = """
            SELECT p.name_es
            FROM sales s
            JOIN sale_lines sl ON sl.sale_id = s.id
            JOIN products p   ON p.id = sl.product_id
            WHERE s.created_at BETWEEN :from AND :to
              AND s.status = 'ACTIVE'
            GROUP BY p.id, p.name_es
            ORDER BY SUM(sl.quantity) DESC
            LIMIT 1
            """, nativeQuery = true)
    String findTopProductNameBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    @Query("SELECT s FROM Sale s WHERE s.customer.id = :customerId ORDER BY s.createdAt DESC")
    List<Sale> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    @Query("SELECT new com.proconsi.electrobazar.dto.WorkerSaleStatsDTO(w.id, w.username, COUNT(s), " +
           "SUM(s.totalAmount), " +
           "SUM(CASE WHEN s.paymentMethod = com.proconsi.electrobazar.model.PaymentMethod.CASH THEN s.totalAmount END), " +
           "SUM(CASE WHEN s.paymentMethod = com.proconsi.electrobazar.model.PaymentMethod.CARD THEN s.totalAmount END)) " +
           "FROM Sale s JOIN s.worker w " +
           "WHERE s.createdAt BETWEEN :from AND :to AND s.status = com.proconsi.electrobazar.model.Sale.SaleStatus.ACTIVE " +
           "GROUP BY w.id, w.username")
    List<WorkerSaleStatsDTO> getWorkerStatsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Aggregates daily revenue for charting — native SQL with composite index on (created_at, status).
     */
    @Query(value = """
            SELECT DATE(created_at) AS date, SUM(total_amount) AS total
            FROM   sales
            WHERE  created_at BETWEEN :from AND :to
              AND  status = 'ACTIVE'
            GROUP  BY DATE(created_at)
            ORDER  BY date ASC
            """, nativeQuery = true)
    List<Object[]> getDailyRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT COALESCE(c.name_es, 'Sin Categoría') AS category, SUM(sl.subtotal) AS total
            FROM   sales s
            JOIN   sale_lines sl ON sl.sale_id = s.id
            JOIN   products   p  ON p.id = sl.product_id
            LEFT   JOIN categories c ON c.id = p.category_id
            WHERE  s.created_at BETWEEN :from AND :to
              AND  s.status = 'ACTIVE'
            GROUP  BY c.id, c.name_es
            ORDER  BY total DESC
            """, nativeQuery = true)
    List<Object[]> getCategoryDistribution(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Aggregates revenue by hour of day (0-23).
     */
    @Query(value = """
            SELECT HOUR(created_at) AS hour, SUM(total_amount) AS total
            FROM   sales
            WHERE  created_at BETWEEN :from AND :to
              AND  status = 'ACTIVE'
            GROUP  BY HOUR(created_at)
            ORDER  BY hour ASC
            """, nativeQuery = true)
    List<Object[]> getHourlyTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Gets top 10 products by revenue.
     */
    @Query(value = """
            SELECT p.name_es AS product, SUM(sl.subtotal) AS revenue
            FROM   sales s
            JOIN   sale_lines sl ON sl.sale_id = s.id
            JOIN   products   p  ON p.id = sl.product_id
            WHERE  s.created_at BETWEEN :from AND :to
              AND  s.status = 'ACTIVE'
            GROUP  BY p.id, p.name_es
            ORDER  BY revenue DESC
            LIMIT  10
            """, nativeQuery = true)
    List<Object[]> getTopProductsDetailed(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    @Query("SELECT s FROM Sale s WHERE s.createdAt BETWEEN :from AND :to ORDER BY s.createdAt DESC")
    Page<Sale> findByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @EntityGraph(attributePaths = { "lines", "lines.product", "customer", "worker", "coupon" })
    @Query("SELECT s FROM Sale s WHERE s.createdAt BETWEEN :from AND :to AND s.worker.id = :workerId ORDER BY s.createdAt DESC")
    Page<Sale> findByCreatedAtBetweenAndWorkerId(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("workerId") Long workerId, Pageable pageable);

    @Query("SELECT HOUR(s.createdAt), SUM(sl.subtotal) " +
           "FROM Sale s JOIN s.lines sl " +
           "WHERE s.createdAt BETWEEN :from AND :to " +
           "AND s.status = 'ACTIVE' " +
           "GROUP BY HOUR(s.createdAt) ORDER BY HOUR(s.createdAt)")
    List<Object[]> getHourlyRevenue(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("SELECT p.nameEs, SUM(sl.subtotal) as total " +
           "FROM Sale s JOIN s.lines sl JOIN sl.product p " +
           "WHERE s.createdAt BETWEEN :from AND :to " +
           "AND s.status = 'ACTIVE' " +
           "GROUP BY p.id, p.nameEs ORDER BY total DESC")
    List<Object[]> getTopProducts(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "customer", "worker", "invoice", "ticket" })
    org.springframework.data.domain.Slice<Sale> findSliceBy(org.springframework.data.jpa.domain.Specification<Sale> spec, org.springframework.data.domain.Pageable pageable);
}