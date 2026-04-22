package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity representing pre-calculated daily sales statistics.
 * Acts as a materialized view to provide instant dashboard and analytics performance.
 */
@Entity
@Table(name = "daily_sales_stats", indexes = {
        @Index(name = "idx_daily_stats_date", columnList = "date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySaleSummary {

    @Id
    private LocalDate date;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private long salesCount = 0;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cashTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cardTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private long cancelledCount = 0;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cancelledTotal = BigDecimal.ZERO;

    @Column(name = "returns_count", nullable = false)
    @Builder.Default
    private long returnsCount = 0;

    @Column(name = "returns_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal returnsTotal = BigDecimal.ZERO;
}
