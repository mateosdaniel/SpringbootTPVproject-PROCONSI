package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

/**
 * REST Controller for managing Workers and authentication.
 * Handles user management and Provides secure login via JWT tokens.
 */
@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerApiRestController {

    private final WorkerService workerService;
    private final JwtService jwtService;

    /**
     * Retrieves all workers.
     * @return List of {@link Worker} entities.
     */
    @GetMapping
    public ResponseEntity<List<Worker>> getAll() {
        return ResponseEntity.ok(workerService.findAll());
    }

    /**
     * Retrieves details of a specific worker.
     * @param id Worker ID.
     * @return 200 OK or 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Worker> getById(@PathVariable Long id) {
        return workerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Registers a new worker.
     * @param worker Worker details.
     * @return 201 Created.
     */
    @PostMapping
    public ResponseEntity<Worker> create(@Valid @RequestBody Worker worker) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workerService.save(worker));
    }

    /**
     * Updates an existing worker's info or permissions.
     * @param id Worker ID.
     * @param worker New details.
     * @return 200 OK.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Worker> update(@PathVariable Long id, @Valid @RequestBody Worker worker) {
        worker.setId(id);
        return ResponseEntity.ok(workerService.save(worker));
    }

    /**
     * Permanently deletes a worker.
     * @param id Worker ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workerService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Authenticates a worker and returns a JWT token.
     * The token includes the worker's ID and permissions.
     * 
     * @param credentials Map with 'username' and 'password'.
     * @return 200 OK with worker info and 'token', or 401 Unauthorized.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }

        Optional<Worker> workerOpt = workerService.login(username, password);

        if (workerOpt.isPresent()) {
            Worker worker = workerOpt.get();
            String token = jwtService.generateToken(worker.getUsername(), worker.getId(), worker.getEffectivePermissions());

            Map<String, Object> response = new HashMap<>();
            response.put("worker", worker);
            response.put("token", token);

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password"));
        }
    }
}
