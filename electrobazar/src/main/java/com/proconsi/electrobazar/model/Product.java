package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Entity representing a product in the inventory.
 * Contains pricing logic to sync net base price and gross price with VAT.
 */
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_active_name", columnList = "active, name_es"),
        @Index(name = "idx_product_category", columnList = "category_id"),
        @Index(name = "idx_product_stock", columnList = "stock")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Name of the product (Spanish). */
    @Column(name = "name_es", nullable = false, length = 150)
    private String nameEs;

    /** Name of the product (English). */
    @Column(name = "name_en", length = 150)
    private String nameEn;

    /** Short description/details of the product (Spanish). */
    @Column(name = "description_es", length = 255)
    private String descriptionEs;

    /** Short description/details of the product (English). */
    @Column(name = "description_en", length = 255)
    private String descriptionEn;

    /** Status of the product (Spanish). */
    @Column(name = "status_es", length = 100)
    private String statusEs;

    /** Status of the product (English). */
    @Column(name = "status_en", length = 100)
    private String statusEn;

    /** Low stock message (Spanish). */
    @Column(name = "low_stock_message_es", length = 255)
    private String lowStockMessageEs;

    /** Low stock message (English). */
    @Column(name = "low_stock_message_en", length = 255)
    private String lowStockMessageEn;

    // --- Compatibility Methods ---
    public String getName() { return nameEs; }
    public void setName(String name) { this.nameEs = name; }
    public String getDescription() { return descriptionEs; }
    public void setDescription(String description) { this.descriptionEs = description; }
    public String getStatus() { return statusEs; }
    public void setStatus(String status) { this.statusEs = status; }
    public String getLowStockMessage() { return lowStockMessageEs; }
    public void setLowStockMessage(String lowStockMessage) { this.lowStockMessageEs = lowStockMessage; }

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

    public String getStatus(java.util.Locale locale) {
        if (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) {
            return (statusEn != null && !statusEn.isBlank()) ? statusEn : statusEs;
        }
        return statusEs;
    }

    public String getLowStockMessage(java.util.Locale locale) {
        if (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) {
            return (lowStockMessageEn != null && !lowStockMessageEn.isBlank()) ? lowStockMessageEn : lowStockMessageEs;
        }
        return lowStockMessageEs;
    }
    // --- End Compatibility Methods ---

    /**
     * The gross price (VAT included) stored in the database.
     * Calculated as: base_price_net * (1 + taxRate.vatRate)
     */
    @Column(name = "price", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "base_price_net", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal basePriceNet = BigDecimal.ZERO;


    /**
     * Sets the Net price based on a Gross price input.
     * basePriceNet = grossPrice / (1 + ivaRate)
     * Also updates the persisted price field.
     * @param grossPrice The price including VAT.
     */
    public void setPrice(BigDecimal grossPrice) {
        if (grossPrice == null) {
            this.basePriceNet = BigDecimal.ZERO;
            this.price = BigDecimal.ZERO;
            return;
        }
        BigDecimal rate = taxRate != null && taxRate.getVatRate() != null ? taxRate.getVatRate()
                : BigDecimal.ZERO;
        this.basePriceNet = grossPrice.divide(BigDecimal.ONE.add(rate), 10, RoundingMode.HALF_UP)
                .setScale(4, RoundingMode.HALF_UP);
        this.price = grossPrice.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Callback method to synchronize the price field before persist/update.
     * Ensures price = basePriceNet * (1 + taxRate.vatRate)
     */
    @PrePersist
    @PreUpdate
    private void syncPrice() {
        if (basePriceNet == null) {
            basePriceNet = BigDecimal.ZERO;
        }
        BigDecimal rate = taxRate != null && taxRate.getVatRate() != null ? taxRate.getVatRate()
                : BigDecimal.ZERO;
        this.price = basePriceNet.multiply(BigDecimal.ONE.add(rate))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /** Available items in stock. */
    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private java.math.BigDecimal stock = java.math.BigDecimal.ZERO;

    /** Whether the product is available for sale. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** URL for the product image. */
    @Column(length = 500)
    private String imageUrl;

    /** Category this product belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Tax rate applicable to this product. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_rate_id", nullable = false)
    private TaxRate taxRate;

    /** Measurement unit for this product (Liter, Kg, Unit). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_unit_id")
    private MeasurementUnit measurementUnit;

    @Column(name = "sales_rank")
    @Builder.Default
    private Integer salesRank = 0;
}