package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a return/refund operation linked to an original sale.
 * Sales are never deleted; returns are compensatory movements that restore
 * stock and issue a refund. Named SaleReturn to avoid collision with
 * the Java keyword 'return'.
 */
@Entity
@Table(name = "returns", indexes = {
        @Index(name = "idx_returns_sale_id", columnList = "sale_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correlative return number, e.g. D-2026-0001. */
    @Column(name = "return_number", nullable = false, unique = true, length = 20)
    private String returnNumber;

    /** The original sale being (partially or fully) returned. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale originalSale;

    /** Timestamp of when the return was processed. Set automatically on persist. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** The worker who processed the return. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    /** Reason for the return, as stated by the worker. */
    @Column(length = 500)
    private String reason;

    /** Whether this is a full or partial return. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnType type;

    /** Total monetary amount refunded. */
    @Column(name = "total_refunded", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalRefunded;

    /** How the refund was paid back to the customer. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /** Status of the return (e.g. PROCESSED). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReturnStatus status = ReturnStatus.COMPLETED;

    /** The individual product lines being returned. */
    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReturnLine> lines = new ArrayList<>();

    public enum ReturnType {
        TOTAL,
        PARTIAL
    }

    public enum ReturnStatus {
        COMPLETED,
        CANCELLED
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
