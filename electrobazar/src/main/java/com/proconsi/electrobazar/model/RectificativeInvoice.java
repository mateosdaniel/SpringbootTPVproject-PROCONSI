package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a corrective invoice (Factura Rectificativa) generated for a return.
 * Used when the original sale was an invoice.
 */
@Entity
@Table(name = "rectificative_invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RectificativeInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Formatted number, e.g. FR-2026-0001. */
    @Column(name = "rectificative_number", nullable = false, unique = true, length = 20)
    private String rectificativeNumber;

    /** The return record this invoice belongs to. */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_return_id", nullable = false, unique = true)
    private SaleReturn saleReturn;

    /** Reference to the original invoice being corrected. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id", nullable = false)
    private Invoice originalInvoice;

    /** When this record was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Mandatory reason for rectification. */
    @Column(nullable = false)
    private String reason;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}


