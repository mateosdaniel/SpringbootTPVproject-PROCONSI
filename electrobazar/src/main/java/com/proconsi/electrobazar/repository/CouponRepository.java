package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for {@link Coupon} access.
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Finds a coupon by its unique code string.
     */
    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * Lists all active coupons that are currently valid by date.
     */
    @Query("SELECT c FROM Coupon c WHERE c.active = true " +
           "AND (c.validFrom IS NULL OR c.validFrom <= :now) " +
           "AND (c.validUntil IS NULL OR c.validUntil >= :now) " +
           "AND (c.usageLimit IS NULL OR c.timesUsed < c.usageLimit)")
    List<Coupon> findValidAt(LocalDateTime now);

    /** Searches coupons by name or code string fragment. */
    List<Coupon> findByCodeContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String code, String desc);
}
