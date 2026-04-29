package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a legal invoice or simplified ticket linked to a Sale.
 * Each sale with a customer generates exactly one Invoice with a correlative
 * number
 * in the format F-YYYY-N (e.g. F-2026-1).
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

    /** Formatted invoice number, e.g. F-2026-1. Unique and non-nullable. */
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

    /** The sale this invoice is associated with. One sale -> one invoice. */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, unique = true)
    private Sale sale;

    /** Timestamp of invoice creation. Set automatically on persist. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Invoice lifecycle status (ACTIVE or RECTIFIED). */
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

    @Column(name = "hash_previous_invoice", nullable = false, length = 64)
    private String hashPreviousInvoice;

    @Column(name = "hash_current_invoice", nullable = false, length = 64)
    private String hashCurrentInvoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "aeat_status", length = 30)
    private AeatStatus aeatStatus;

    @Column(name = "aeat_submission_date")
    private java.time.LocalDateTime aeatSubmissionDate;

    @Column(name = "aeat_last_error", columnDefinition = "TEXT")
    private String aeatLastError;

    @Column(name = "aeat_retry_count", nullable = false)
    @Builder.Default
    private int aeatRetryCount = 0;

    @Column(name = "aeat_raw_response", columnDefinition = "TEXT")
    private String aeatRawResponse;

    @Column(name = "aeat_wait_time")
    private Integer aeatWaitTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "aeat_rejection_reason", length = 30)
    private AeatRejectionReason aeatRejectionReason;

    /**
     * Possible statuses for an invoice.
     */
    public enum InvoiceStatus {
        ACTIVE,
        RECTIFIED
    }

    @Column(name = "aeat_csv", length = 20)
    private String aeatCsv;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
