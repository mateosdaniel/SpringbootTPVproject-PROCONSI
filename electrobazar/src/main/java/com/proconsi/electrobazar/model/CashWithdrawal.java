package com.proconsi.electrobazar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity tracking manual cash movements (entries or withdrawals) in a shift.
 */
@Entity
@Table(name = "cash_withdrawals", indexes = {
        @Index(name = "idx_withdrawals_register_id", columnList = "cash_register_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashWithdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The shift associated with this movement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_id", nullable = false)
    @JsonIgnore
    private CashRegister cashRegister;

    /** Amount of money moved. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Reason for the movement. */
    @Column(length = 255)
    private String reason;

    /** Worker who authorized/performed the movement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    /** When the movement occurred. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Type of movement: ENTRY (deposit) or WITHDRAWAL (exit). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MovementType type = MovementType.WITHDRAWAL;

    /**
     * Enumeration for the direction of cash movement.
     */
    public enum MovementType {
        WITHDRAWAL, // Exit
        ENTRY // Add
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
