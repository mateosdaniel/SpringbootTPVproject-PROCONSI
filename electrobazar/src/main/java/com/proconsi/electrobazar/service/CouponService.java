package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service for Discount Coupon management and validation.
 */
public interface CouponService {

    /**
     * Finds a coupon by its unique code string.
     */
    Optional<Coupon> findByCode(String code);

    /**
     * Lists all coupons in the system.
     */
    List<Coupon> findAll();

    /**
     * Paged access to coupons.
     */
    Page<Coupon> findAllPageable(Pageable pageable);

    /**
     * Retrieves an individual coupon by its ID.
     */
    Coupon findById(Long id);

    /**
     * Saves or creates a new discount coupon.
     */
    Coupon save(Coupon coupon);

    /**
     * Logic for deleting a coupon.
     */
    void delete(Long id);

    /**
     * Toggles the active state of a coupon.
     */
    void toggleActive(Long id);

    /**
     * Validates if a code string represents a currently valid and applicable coupon.
     */
    Optional<Coupon> validate(String code);

    /**
     * Marks a coupon as used after a successful sale (increments timesUsed).
     */
    void incrementUsage(Long id);
}
