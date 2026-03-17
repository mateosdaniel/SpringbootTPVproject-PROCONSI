package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Entity representing a product category.
 */
@Entity
@Table(name = "categories")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique name of the category. */
    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Short description of the category. */
    @Column(length = 255)
    private String description;

    /** Whether the category is active and visible in the TPV. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Products associated with this category. */
    @JsonIgnore
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    /** Default VAT rate for products in this category. */
    @Column(name = "iva_rate", precision = 5, scale = 4)
    private BigDecimal ivaRate;
}