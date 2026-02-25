package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.service.AdminPinService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminLoginController {

    private final AdminPinService adminPinService;

    public AdminLoginController(AdminPinService adminPinService) {
        this.adminPinService = adminPinService;
    }

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpSession session) {
        String pin = body.getOrDefault("pin", "");
        if (adminPinService.verifyPin(pin)) {
            session.setAttribute("admin", true);
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(401).body(Map.of("ok", false, "message", "PIN incorrecto"));
    }

    @PostMapping("/change-pin")
    @ResponseBody
    public ResponseEntity<?> changePin(@RequestBody Map<String, String> body, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin"))) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "message", "No autorizado"));
        }

        String oldPin = body.getOrDefault("oldPin", "");
        String newPin = body.getOrDefault("newPin", "");

        if (adminPinService.changePin(oldPin, newPin)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(400)
                .body(Map.of("ok", false, "message", "No se pudo cambiar el PIN verifique el PIN actual."));
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/tpv";
    }
}
