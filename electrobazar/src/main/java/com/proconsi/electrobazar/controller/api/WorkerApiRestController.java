package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerApiRestController {

    private final WorkerService workerService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }

        Optional<Worker> worker = workerService.login(username, password);

        if (worker.isPresent()) {
            return ResponseEntity.ok(worker.get());
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }
    }
}
