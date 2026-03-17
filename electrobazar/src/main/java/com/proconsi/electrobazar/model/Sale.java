package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a completed sale transaction.
 * Tracks totals, payment details, and linked documents (invoice/ticket).
 */
@Entity
@Table(name = "sales", indexes = {
        @Index(name = "idx_sales_created_at", columnList = "created_at"),
        @Index(name = "idx_sales_payment_method", columnList = "payment_method")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Timestamp of sale completion. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** Customer associated with the sale (if any). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Worker who performed the sale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    /** Selected payment method for the transaction. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /** Final amount paid by the customer (inclusive of all taxes). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** Total amount handed over by the customer. */
    @Column(precision = 10, scale = 2)
    private BigDecimal receivedAmount;

    /** Change returned to the customer. */
    @Column(precision = 10, scale = 2)
    private BigDecimal changeAmount;

    /** Portion of the total paid in cash. */
    @Column(precision = 10, scale = 2)
    private BigDecimal cashAmount;

    /** Portion of the total paid by card. */
    @Column(precision = 10, scale = 2)
    private BigDecimal cardAmount;

    /** Aggregate taxable base across all lines. */
    @Column(nullable = false, precision = 10, scale = 2, name = "total_base")
    @Builder.Default
    private BigDecimal totalBase = BigDecimal.ZERO;

    /** Aggregate VAT amount. */
    @Column(nullable = false, precision = 10, scale = 2, name = "total_vat")
    @Builder.Default
    private BigDecimal totalVat = BigDecimal.ZERO;

    /** Aggregate recargo de equivalencia amount. */
    @Column(nullable = false, precision = 10, scale = 2, name = "total_recargo")
    @Builder.Default
    private BigDecimal totalRecargo = BigDecimal.ZERO;

    /** Internal notes or comments. */
    @Column(length = 255)
    private String notes;

    /** Product lines included in the sale. */
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleLine> lines = new ArrayList<>();

    /** Legal invoice associated with the sale (if generated). */
    @OneToOne(mappedBy = "sale")
    private Invoice invoice;

    /** Simplified ticket associated with the sale (if generated). */
    @OneToOne(mappedBy = "sale")
    private Ticket ticket;

    /** Whether the customer was subject to Recargo de Equivalencia at checkout time. */
    @Column(nullable = false)
    @Builder.Default
    private boolean applyRecargo = false;

    /** Logical status of the sale (ACTIVE or CANCELLED). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleStatus status = SaleStatus.ACTIVE;

    /** Name of the tariff applied (for historical purposes). */
    @Column(length = 50)
    @Builder.Default
    private String appliedTariff = "MINORISTA";

    /** Discount percentage applied to the sale header. */
    @Column(nullable = false, precision = 5, scale = 2, name = "applied_discount_percentage")
    @Builder.Default
    private BigDecimal appliedDiscountPercentage = BigDecimal.ZERO;

    /** Combined discount amount for informational purposes. */
    @Column(nullable = false, precision = 10, scale = 2, name = "total_discount")
    @Builder.Default
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    /**
     * Enumeration for the sale lifecycle state.
     */
    public enum SaleStatus {
        ACTIVE,
        CANCELLED
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}