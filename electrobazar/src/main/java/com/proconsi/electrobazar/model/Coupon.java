package com.proconsi.electrobazar.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for discount coupons that can be applied to TPV sales.
 */
@Entity
@Table(name = "coupons", indexes = {
        @Index(name = "idx_coupons_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique code string (e.g., "SUMMER10"). */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Description of the discount or campaign. */
    @Column(length = 255)
    private String description;

    /** Type of discount applied: PERCENTAGE or FIXED_AMOUNT. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    /**
     * Value of the discount depending on type:
     * - Percentage (e.g., 15 for 15%)
     * - Fixed (e.g., 5.00 for 5.00€)
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** Whether the coupon is currently active. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Optional start date and time for validity. */
    private LocalDateTime validFrom;

    /** Optional end date and time for expiration. */
    private LocalDateTime validUntil;

    /** Optional maximum number of times it can be used overall across all customers. */
    private Integer usageLimit;

    /** Counter for how many times the coupon has been applied. */
    @Builder.Default
    private Integer timesUsed = 0;

    /** Record of when the coupon template was created. */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /** Indicates if this coupon was auto-assigned to a customer or is generic. */
    @Builder.Default
    private Boolean generic = true;

    /**
     * Checks if the coupon is currently valid based on dates and usage limits.
     */
    public boolean isValid() {
        if (!Boolean.TRUE.equals(active)) return false;
        
        LocalDateTime now = LocalDateTime.now();
        if (validFrom != null && now.isBefore(validFrom)) return false;
        if (validUntil != null && now.isAfter(validUntil)) return false;
        
        if (usageLimit != null && usageLimit > 0 && timesUsed >= usageLimit) return false;
        
        return true;
    }
}
