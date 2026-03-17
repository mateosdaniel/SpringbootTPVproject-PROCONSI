package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a cash register shift/session.
 * Tracks all financial movements during a day or shift.
 */
@Entity
@Table(name = "cash_registers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The logic date for this register entry. */
    @Column(nullable = false)
    private LocalDate registerDate;

    /** Starting amount in the drawer. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /** Total accumulation of sales paid in cash. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cashSales = BigDecimal.ZERO;

    /** Total accumulation of sales paid by card. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cardSales = BigDecimal.ZERO;

    /** Combined total of all sales. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    /** Theoretical amount that should be in the drawer at closing. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal closingBalance = BigDecimal.ZERO;

    /** Total amount returned to customers in cash. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cashRefunds = BigDecimal.ZERO;

    /** Total amount returned via card. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cardRefunds = BigDecimal.ZERO;

    /** Accumulation of manual cash withdrawals. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalWithdrawals = BigDecimal.ZERO;

    /** Accumulation of manual cash entries. */
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalEntries = BigDecimal.ZERO;

    /** Difference between theoretical and actual cash (if counted). */
    @Column(precision = 10, scale = 2)
    private BigDecimal difference;

    /** Optional notes about the shift. */
    @Column(length = 255)
    private String notes;

    /** Wall-clock time when the register was opened. */
    @Column(nullable = true)
    private LocalDateTime openingTime;

    /** Wall-clock time when the register was closed. */
    @Column(nullable = true)
    private LocalDateTime closedAt;

    /** Flag indicating if the shift is finalized. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean closed = false;

    /** The worker who opened/owns this shift. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    /** Amount specifically left in the drawer for the next shift. */
    @Column(precision = 10, scale = 2, nullable = true)
    private BigDecimal retainedForNextShift;

    /** The worker who performed the closing and retention. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retained_by_worker_id", nullable = true)
    private Worker retainedByWorker;

    /** Detailed list of manual cash movements. */
    @OneToMany(mappedBy = "cashRegister", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.List<CashWithdrawal> withdrawals = new java.util.ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.registerDate == null) {
            this.registerDate = LocalDate.now();
        }
    }
}
