package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A sale that has been suspended mid-process by a worker.
 * Persisted so the cart survives page reloads.
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

    /** Timestamp when the sale was first suspended. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp of the last status change. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** The worker who suspended the sale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    /** Optional descriptive label for the suspended sale. */
    @Column(length = 100)
    private String label;

    /** Lifecycle status (SUSPENDED, RESUMED, or CANCELLED). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SuspendedSaleStatus status = SuspendedSaleStatus.SUSPENDED;

    /** The product lines in the cart when suspended. */
    @OneToMany(mappedBy = "suspendedSale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SuspendedSaleLine> lines = new ArrayList<>();

    /**
     * Enumeration for suspended sale lifecycle.
     */
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


