package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a simplified ticket (factura simplificada) linked to a Sale.
 * Used for anonymous sales or when a full invoice is not requested.
 */
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_tickets_sale_id", columnList = "sale_id"),
        @Index(name = "idx_tickets_number", columnList = "ticket_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Formatted ticket number, e.g. T-2026-0001. Unique and non-nullable. */
    @Column(name = "ticket_number", nullable = false, unique = true, length = 20)
    private String ticketNumber;

    /** Series prefix, e.g. "T". */
    @Column(name = "serie", nullable = false, length = 5)
    private String serie;

    /** The year this ticket belongs to (e.g. 2026). */
    @Column(name = "year", nullable = false)
    private int year;

    /** The sequential number within the serie+year (e.g. 1, 2, 3...). */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    /** The sale this ticket is associated with. One sale -> one ticket. */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, unique = true)
    private Sale sale;

    /** Timestamp of ticket creation. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Whether RE tax was applied to this ticket.
     */
    @Column(nullable = false)
    private boolean applyRecargo;

    @Column(name = "hash_previous_invoice", nullable = false, length = 64)
    private String hashPreviousInvoice;

    @Column(name = "hash_current_invoice", nullable = false, length = 64)
    private String hashCurrentInvoice;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
