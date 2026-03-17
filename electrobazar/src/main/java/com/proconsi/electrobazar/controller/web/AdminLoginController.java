package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.service.AdminPinService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles administrative specific login and PIN verification to escalate privileges.
 */
@Controller
@RequestMapping("/admin")
public class AdminLoginController {

    private final AdminPinService adminPinService;

    public AdminLoginController(AdminPinService adminPinService) {
        this.adminPinService = adminPinService;
    }

    /**
     * Verifies the administrator PIN and escalates session privileges.
     */
    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String pin = body.getOrDefault("pin", "");
        if (adminPinService.verifyPin(pin)) {
            session.setAttribute("admin", true);

            // Escalate privileges in SecurityContext
            org.springframework.security.core.Authentication currentAuth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
            String username = "admin";

            if (currentAuth != null) {
                username = currentAuth.getName();
                authorities.addAll(currentAuth.getAuthorities().stream()
                        .map(a -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                a.getAuthority()))
                        .collect(java.util.stream.Collectors.toList()));
            }

            if (authorities.stream().noneMatch(a -> a.getAuthority().equals("ADMIN_ACCESS"))) {
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN_ACCESS"));
            }

            org.springframework.security.authentication.UsernamePasswordAuthenticationToken newAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    username, null, authorities);

            org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder
                    .getContext();
            context.setAuthentication(newAuth);
            session.setAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context);

            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(401).body(Map.of("ok", false, "message", "Incorrect PIN"));
    }

    /**
     * Updates the admin PIN after verifying current PIN and matching new PIN.
     */
    @PostMapping("/settings/pin")
    @ResponseBody
    public ResponseEntity<?> changePin(@RequestBody Map<String, String> body, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Access denied. Admin session required."));
        }

        String currentPin = body.getOrDefault("currentPin", "");
        String newPin = body.getOrDefault("newPin", "");
        String confirmPin = body.getOrDefault("confirmPin", "");

        if (!newPin.equals(confirmPin)) {
            return ResponseEntity.badRequest().body(Map.of("message", "The new PIN and confirmation do not match."));
        }

        try {
            adminPinService.updatePin(currentPin, newPin);
            return ResponseEntity.ok(Map.of("ok", true, "message", "PIN updated successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Log out from admin session and redirect to storefront.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/tpv";
    }
}
