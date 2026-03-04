package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;

    public List<Worker> findAll() {
        return workerRepository.findAll();
    }

    public List<Worker> findAllActive() {
        return workerRepository.findAll().stream()
                .filter(Worker::isActive)
                .toList();
    }

    public Optional<Worker> findById(Long id) {
        return workerRepository.findById(id);
    }

    public Worker save(Worker worker) {
        boolean isNew = worker.getId() == null;

        if (!isNew) {
            // Updating an existing worker
            Worker existing = workerRepository.findById(worker.getId()).orElse(null);
            if (existing != null) {
                String submitted = worker.getPassword();
                if (submitted == null || submitted.trim().isEmpty()) {
                    // No new password provided → keep the existing hash
                    worker.setPassword(existing.getPassword());
                } else if (!submitted.startsWith("$2")) {
                    // A new plain-text password was submitted → hash it
                    worker.setPassword(passwordEncoder.encode(submitted.trim()));
                }
                // If it already starts with "$2" it's already a BCrypt hash — leave it as-is
            }
        } else {
            // New worker: hash the plain-text password before saving
            if (worker.getPassword() != null && !worker.getPassword().trim().isEmpty()) {
                worker.setPassword(passwordEncoder.encode(worker.getPassword().trim()));
            }
        }

        Worker saved = workerRepository.save(worker);

        activityLogService.logActivity(
                isNew ? "CREAR_TRABAJADOR" : "ACTUALIZAR_TRABAJADOR",
                (isNew ? "Nuevo trabajador registrado: " : "Trabajador actualizado: ") + saved.getUsername(),
                "Admin",
                "WORKER",
                saved.getId());

        return saved;
    }

    public void deleteById(Long id) {
        workerRepository.findById(id).ifPresent(w -> {
            workerRepository.deleteById(id);
            activityLogService.logActivity(
                    "ELIMINAR_TRABAJADOR",
                    "Trabajador eliminado definitivamente: " + w.getUsername(),
                    "Admin",
                    "WORKER",
                    id);
        });
    }

    /**
     * Authenticates a worker by verifying their plain-text password against
     * the stored BCrypt hash using {@code passwordEncoder.matches()}.
     */
    public Optional<Worker> login(String username, String password) {
        Optional<Worker> workerOpt = workerRepository.findByUsername(username)
                .filter(Worker::isActive)
                .filter(w -> passwordEncoder.matches(password, w.getPassword()));

        if (workerOpt.isPresent()) {
            activityLogService.logActivity(
                    "LOGIN",
                    "Inicio de sesión exitoso: " + username,
                    username,
                    "WORKER",
                    workerOpt.get().getId());
        } else {
            activityLogService.logActivity(
                    "LOGIN_FALLIDO",
                    "Intento de inicio de sesión fallido: " + username,
                    "Sistema",
                    "WORKER",
                    null);
        }

        return workerOpt;
    }
}
