package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "sale_lines", indexes = {
        @Index(name = "idx_sale_lines_sale_id", columnList = "sale_id"),
        @Index(name = "idx_sale_lines_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2, name = "base_price_net")
    @Builder.Default
    private BigDecimal basePriceNet = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 4, name = "vat_rate")
    private BigDecimal vatRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2, name = "base_amount")
    private BigDecimal baseAmount;

    @Column(nullable = false, precision = 10, scale = 2, name = "vat_amount")
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 5, scale = 4, name = "recargo_rate")
    private BigDecimal recargoRate;

    @Column(nullable = false, precision = 10, scale = 2, name = "recargo_amount")
    private BigDecimal recargoAmount;
}