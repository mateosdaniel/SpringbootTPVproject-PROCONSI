package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.WorkerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Map;
import org.springframework.http.ResponseEntity;

/**
 * Handles user authentication for the storefront (TPV).
 */
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final WorkerService workerService;
    private final com.proconsi.electrobazar.service.ActivityLogService activityLogService;

    @GetMapping("/login")
    public String loginForm(HttpServletRequest request, Model model) {
        // Esto fuerza la creación de la sesión antes de que se empiece a pintar el HTML
        request.getSession(true);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session,
            Model model) {
        Optional<Worker> worker = workerService.login(username, password);
        if (worker.isPresent()) {
            Worker w = worker.get();
            session.setAttribute("worker", w);

            // Populate Spring Security Context for session-based auth
            java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = w
                    .getEffectivePermissions()
                    .stream()
                    .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                    .collect(java.util.stream.Collectors.toList());

            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    w.getUsername(), null, authorities);

            org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder
                    .createEmptyContext();
            context.setAuthentication(auth);
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            session.setAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context);

            // If has admin access permission, set admin session attribute
            if (w.getEffectivePermissions().contains("ADMIN_ACCESS")) {
                session.setAttribute("admin", true);
            }
            activityLogService.logFiscalEvent("LOGIN", "Inicio de sesión de trabajador: " + w.getUsername(),
                    w.getUsername());
            return "redirect:/tpv";
        } else {
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
    }

    /**
     * Invalidate session and redirect to login page.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        Worker w = (Worker) session.getAttribute("worker");
        if (w != null) {
            activityLogService.logFiscalEvent("LOGOUT", "Cierre de sesión de trabajador: " + w.getUsername(),
                    w.getUsername());
        }
        session.invalidate();
        return "redirect:/login";
    }

    /**
     * Endpoint to request a password reset PIN.
     */
    @PostMapping("/forgot-password")
    @ResponseBody
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        boolean sent = workerService.initiatePasswordReset(email);
        if (sent) {
            return ResponseEntity.ok(Map.of("ok", true, "message", "Reset PIN sent to email"));
        } else {
            // We return 200 anyway for security (don't reveal if email exists)
            // But actually in a private TPV it might be better to say it doesn't exist.
            // Let's stick to standard practice.
            return ResponseEntity.ok(Map.of("ok", true, "message", "If the email is registered, a PIN has been sent."));
        }
    }

    /**
     * Endpoint to reset the password using the PIN.
     */
    @PostMapping("/reset-password")
    @ResponseBody
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String pin = body.get("pin");
        String password = body.get("password");

        if (email == null || pin == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing required fields"));
        }

        boolean success = workerService.resetPassword(email, pin, password);
        if (success) {
            return ResponseEntity.ok(Map.of("ok", true, "message", "Password updated successfully"));
        } else {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired PIN"));
        }
    }
}
