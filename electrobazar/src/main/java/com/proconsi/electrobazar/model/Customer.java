package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Entity representing a customer.
 */
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_tax_id", columnList = "tax_id"),
        @Index(name = "idx_customers_name", columnList = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Primary contact or company name. */
    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Column(nullable = false, length = 150)
    private String name;

    /** NIF, CIF, or other tax identifier. */
    @Column(length = 50)
    private String taxId;

    /** Email address for invoices or contact. */
    @Column(length = 100)
    private String email;

    /** Contact phone number. */
    @Column(length = 20)
    private String phone;

    /** Billing or shipping address. */
    @Column(length = 255)
    private String address;

    /** City. */
    @Column(length = 50)
    private String city;

    /** Postal code. */
    @Column(length = 50)
    private String postalCode;

    /** Classification: INDIVIDUAL or COMPANY. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerType type = CustomerType.INDIVIDUAL;

    /** Whether the customer account is active. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Indicates whether this customer is subject to the Spanish 'Recargo de
     * Equivalencia' (RE).
     * RE applies to retailers who cannot deduct input VAT and therefore pay a surcharge.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasRecargoEquivalencia = false;

    /**
     * Pricing tariff assigned to this customer.
     * When null, the system falls back to standard pricing (MINORISTA).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id")
    private Tariff tariff;

    /**
     * Enumeration for the customer legal type.
     */
    public enum CustomerType {
        INDIVIDUAL,
        COMPANY
    }
}
