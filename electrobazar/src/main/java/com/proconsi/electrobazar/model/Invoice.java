package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a legal invoice or simplified ticket linked to a Sale.
 * Each sale with a customer generates exactly one Invoice with a correlative
 * number
 * in the format F-YYYY-NNNN (e.g. F-2026-0001).
 */
@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_sale_id", columnList = "sale_id"),
        @Index(name = "idx_invoices_number", columnList = "invoice_number"),
        @Index(name = "idx_invoices_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Formatted invoice number, e.g. F-2026-0001. Unique and non-nullable. */
    @Column(name = "invoice_number", nullable = false, unique = true, length = 20)
    private String invoiceNumber;

    /** Series prefix, e.g. "F". */
    @Column(name = "serie", nullable = false, length = 5)
    private String serie;

    /** The year this invoice belongs to (e.g. 2026). */
    @Column(name = "year", nullable = false)
    private int year;

    /** The sequential number within the serie+year (e.g. 1, 2, 3...). */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    /** The sale this invoice is associated with. One sale → one invoice. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, unique = true)
    private Sale sale;

    /** Timestamp of invoice creation. Set automatically on persist. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Invoice lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.ACTIVE;

    /**
     * If this invoice was rectified, points to the rectifying invoice.
     * Nullable — only set after a credit note is issued.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rectified_by_id")
    private Invoice rectifiedBy;

    public enum InvoiceStatus {
        ACTIVE,
        RECTIFIED
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
