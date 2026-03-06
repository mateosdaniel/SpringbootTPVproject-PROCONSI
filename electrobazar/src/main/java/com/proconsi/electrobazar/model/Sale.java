package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal receivedAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal changeAmount;

    @Column(nullable = false, precision = 10, scale = 2, name = "total_base")
    @Builder.Default
    private BigDecimal totalBase = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2, name = "total_vat")
    @Builder.Default
    private BigDecimal totalVat = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2, name = "total_recargo")
    @Builder.Default
    private BigDecimal totalRecargo = BigDecimal.ZERO;

    @Column(length = 255)
    private String notes;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleLine> lines = new ArrayList<>();

    @OneToOne(mappedBy = "sale")
    private Invoice invoice;

    @OneToOne(mappedBy = "sale")
    private Ticket ticket;

    @Column(nullable = false)
    @Builder.Default
    private boolean applyRecargo = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleStatus status = SaleStatus.ACTIVE;

    public enum SaleStatus {
        ACTIVE,
        CANCELLED
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}