package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.WorkerService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final WorkerService workerService;

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session,
            Model model) {
        Optional<Worker> worker = workerService.login(username, password);
        if (worker.isPresent()) {
            Worker w = worker.get();
            session.setAttribute("worker", w);
            // Si tiene permiso de acceso admin, le damos la sesión de admin
            if (w.getEffectivePermissions().contains("ADMIN_ACCESS")) {
                session.setAttribute("admin", true);
            }
            return "redirect:/tpv";
        } else {
            model.addAttribute("error", "Usuario o contraseña incorrectos");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("worker");
        return "redirect:/login";
    }
}
