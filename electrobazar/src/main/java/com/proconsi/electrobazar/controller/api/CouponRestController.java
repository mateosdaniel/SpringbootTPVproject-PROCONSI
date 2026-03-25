package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for coupon validation and status checks.
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponRestController {

    private final CouponRepository couponRepository;

    @GetMapping("/validate")
    public ResponseEntity<?> validateCoupon(@RequestParam String code) {
        return couponRepository.findByCodeIgnoreCase(code.trim())
                .map(coupon -> {
                    if (coupon.isValid()) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("valid", true);
                        response.put("code", coupon.getCode());
                        response.put("discountType", coupon.getDiscountType());
                        response.put("discountValue", coupon.getDiscountValue());
                        response.put("description", coupon.getDescription());
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "El cupón ha expirado o ya no es válido."));
                    }
                })
                .orElse(ResponseEntity.status(404).body(Map.of("valid", false, "error", "Cupón no encontrado.")));
    }
}
