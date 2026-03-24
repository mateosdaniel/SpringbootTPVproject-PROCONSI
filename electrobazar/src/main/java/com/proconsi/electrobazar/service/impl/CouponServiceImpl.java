package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Coupon;
import com.proconsi.electrobazar.model.DiscountType;
import com.proconsi.electrobazar.repository.CouponRepository;
import com.proconsi.electrobazar.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link CouponService} for managing discount coupons.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Coupon> findByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(code);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Coupon> findAll() {
        return couponRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Coupon> findAllPageable(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Coupon findById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found with id: " + id));
    }

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon == null) throw new IllegalArgumentException("Coupon can not be null.");

        // Clean values before saving (e.g., ensure fixed amount doesn't exceed reasonable limits or negative)
        if (coupon.getDiscountType() == null) {
            coupon.setDiscountType(DiscountType.PERCENTAGE);
        }
        
        if (coupon.getDiscountValue() != null && coupon.getDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
            coupon.setDiscountValue(BigDecimal.ZERO);
        }

        // Percentage limit (0-100)
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE 
            && coupon.getDiscountValue() != null 
            && coupon.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
            coupon.setDiscountValue(new BigDecimal("100"));
        }

        return couponRepository.save(coupon);
    }

    @Override
    public void delete(Long id) {
        if (!couponRepository.existsById(id)) return;
        couponRepository.deleteById(id);
    }

    @Override
    public void toggleActive(Long id) {
        Coupon c = findById(id);
        c.setActive(!Boolean.TRUE.equals(c.getActive()));
        couponRepository.save(c);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Coupon> validate(String code) {
        if (code == null || code.trim().isEmpty()) return Optional.empty();
        
        return findByCode(code.trim()).filter(Coupon::isValid);
    }

    @Override
    public void incrementUsage(Long id) {
        Coupon c = findById(id);
        c.setTimesUsed(c.getTimesUsed() + 1);
        couponRepository.save(c);
    }
}
