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

    /** Unique name of the category (Spanish). */
    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Column(name = "name_es", nullable = false, unique = true, length = 100)
    private String nameEs;

    /** Unique name of the category (English). */
    @Column(name = "name_en", length = 100)
    private String nameEn;

    /** Short description of the category (Spanish). */
    @Column(name = "description_es", length = 255)
    private String descriptionEs;

    /** Short description of the category (English). */
    @Column(name = "description_en", length = 255)
    private String descriptionEn;

    // --- Compatibility Methods ---
    public String getName() { return nameEs; }
    public void setName(String name) { this.nameEs = name; }
    public String getDescription() { return descriptionEs; }
    public void setDescription(String description) { this.descriptionEs = description; }

    public String getName(java.util.Locale locale) {
        if (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) {
            return (nameEn != null && !nameEn.isBlank()) ? nameEn : nameEs;
        }
        return nameEs;
    }

    public String getDescription(java.util.Locale locale) {
        if (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) {
            return (descriptionEn != null && !descriptionEn.isBlank()) ? descriptionEn : descriptionEs;
        }
        return descriptionEs;
    }
    // --- End Compatibility Methods ---

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