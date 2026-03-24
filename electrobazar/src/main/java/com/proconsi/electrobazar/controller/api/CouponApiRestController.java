package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Coupon;
import com.proconsi.electrobazar.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Coupon management operations.
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponApiRestController {

    private final CouponService couponService;

    /** Retrieves all coupons. */
    @GetMapping
    public List<Coupon> getAll() {
        return couponService.findAll();
    }

    /** Finds a specific coupon by ID. */
    @GetMapping("/{id}")
    public Coupon getById(@PathVariable Long id) {
        return couponService.findById(id);
    }

    /** Validates a coupon code string. */
    @GetMapping("/validate/{code}")
    public ResponseEntity<?> validateCode(@PathVariable String code) {
        return couponService.validate(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Creates or updates a coupon. */
    @PostMapping
    public Coupon save(@RequestBody Coupon coupon) {
        return couponService.save(coupon);
    }

    /** Deletes a coupon by ID. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ResponseEntity.ok().build();
    }

    /** Toggles the active state of a coupon. */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<Void> toggleActive(@PathVariable Long id) {
        couponService.toggleActive(id);
        return ResponseEntity.ok().build();
    }
}
