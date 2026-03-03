package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String taxId; // NIF/CIF

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 50)
    private String city;

    @Column(length = 50)
    private String postalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerType type = CustomerType.INDIVIDUAL;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Indicates whether this customer is subject to the Spanish 'Recargo de
     * Equivalencia' (RE).
     * RE applies to retailers (autónomos en régimen de recargo de equivalencia) who
     * cannot
     * deduct input VAT and therefore pay a surcharge on top of the standard VAT
     * rate.
     * Only applicable to COMPANY type customers operating under this tax regime.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasRecargoEquivalencia = false;

    public enum CustomerType {
        INDIVIDUAL,
        COMPANY
    }
}
