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
        // Covering index for all analytics queries: WHERE created_at BETWEEN ? AND ? AND status = 'ACTIVE'
        // This replaces the two separate single-column indexes for much better range query performance.
        @Index(name = "idx_sales_created_at_status", columnList = "created_at, status"),
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

    /** Cash session this sale belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

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

    /** Coupon applied to the sale (if any). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    /** Discount percentage applied to the sale header. */
    @Column(nullable = false, precision = 5, scale = 2, name = "applied_discount_percentage")
    @Builder.Default
    private BigDecimal appliedDiscountPercentage = BigDecimal.ZERO;

    /** Combined discount amount for informational purposes. */
    @Column(nullable = false, precision = 10, scale = 2, name = "total_discount")
    @Builder.Default
    private BigDecimal totalDiscount = BigDecimal.ZERO;

    /** Amount paid using a customer's credit/voucher (Abono). */
    @Column(nullable = false, precision = 10, scale = 2, name = "abono_amount")
    @Builder.Default
    private BigDecimal abonoAmount = BigDecimal.ZERO;

    /** Document type generated for this sale. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 30)
    private TipoDocumento tipoDocumento;

    /** JSON snapshot of ad-hoc customer data for one-off invoices (no DB customer saved). */
    @Column(name = "cliente_puntual_json", columnDefinition = "TEXT")
    private String clientePuntualJson;

    /**
     * Returns the customer data map to use when rendering invoice documents.
     * Delegates to BD customer, JSON snapshot, or null (ticket sin cliente).
     */
    public java.util.Map<String, String> getDatosClienteParaFactura() {
        if (customer != null && tipoDocumento == TipoDocumento.FACTURA_COMPLETA) {
            java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("nombre", customer.getName());
            String label = customer.getType() != null && customer.getType().name().equals("COMPANY") ? "CIF" : "NIF";
            m.put("nifLabel", label);
            m.put("nif", customer.getTaxId());
            String addr = customer.getAddress() != null ? customer.getAddress() : "";
            String city = customer.getCity() != null ? customer.getCity() : "";
            String cp = customer.getPostalCode() != null ? customer.getPostalCode() : "";
            m.put("direccion", addr + (addr.isBlank() ? "" : ", ") + cp + " " + city);
            return m;
        }
        if (clientePuntualJson != null && !clientePuntualJson.isBlank()) {
            try {
                org.springframework.boot.json.JsonParser parser = org.springframework.boot.json.JsonParserFactory.getJsonParser();
                java.util.Map<String, Object> raw = parser.parseMap(clientePuntualJson);
                java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
                m.put("nombre", String.valueOf(raw.getOrDefault("nombre", "")));
                m.put("nifLabel", "NIF");
                m.put("nif", String.valueOf(raw.getOrDefault("nif", "")));
                String cp = String.valueOf(raw.getOrDefault("codigoPostal", ""));
                String city = String.valueOf(raw.getOrDefault("ciudad", ""));
                String addr = String.valueOf(raw.getOrDefault("direccion", ""));
                m.put("direccion", addr + (addr.isBlank() ? "" : ", ") + cp + " " + city);
                return m;
            } catch (Exception ignored) {}
        }
        return null;
    }

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