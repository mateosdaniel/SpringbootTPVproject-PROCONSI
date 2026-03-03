package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A sale that has been suspended mid-process by a worker.
 * Persisted in the DB so the cart survives page reloads.
 * The sale can later be resumed (loaded back into the JS cart)
 * or cancelled (discarded without completing a sale).
 */
@Entity
@Table(name = "suspended_sales", indexes = {
        @Index(name = "idx_suspended_sales_status", columnList = "status"),
        @Index(name = "idx_suspended_sales_worker_id", columnList = "worker_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuspendedSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Timestamp when the sale was first suspended. Set automatically on persist.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last status change (suspend → resume / cancel).
     * Set on every save via @PrePersist and @PreUpdate.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** The worker who suspended the sale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    /** Optional descriptive label, e.g. "Cliente esperando tarjeta". */
    @Column(length = 100)
    private String label;

    /** Lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SuspendedSaleStatus status = SuspendedSaleStatus.SUSPENDED;

    /** The product lines that were in the cart when the sale was suspended. */
    @OneToMany(mappedBy = "suspendedSale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SuspendedSaleLine> lines = new ArrayList<>();

    public enum SuspendedSaleStatus {
        SUSPENDED,
        RESUMED,
        CANCELLED
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
