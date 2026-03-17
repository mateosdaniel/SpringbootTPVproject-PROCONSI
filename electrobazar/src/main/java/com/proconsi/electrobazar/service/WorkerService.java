package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing system workers (users).
 * Handles authentication, password hashing, and user CRUD.
 */
@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Retrieves all workers in the system.
     * @return List of all workers.
     */
    public List<Worker> findAll() {
        return workerRepository.findAll();
    }

    /**
     * Retrieves only workers with an active status.
     * @return List of active workers.
     */
    public List<Worker> findAllActive() {
        return workerRepository.findAll().stream()
                .filter(Worker::isActive)
                .toList();
    }

    /**
     * Finds a specific worker by ID.
     * @param id Primary key.
     * @return Optional containing the Worker.
     */
    public Optional<Worker> findById(Long id) {
        return workerRepository.findById(id);
    }

    /**
     * Saves or updates a worker.
     * Automatically handles BCrypt password hashing for new or modified passwords.
     *
     * @param worker The worker entity to save.
     * @return The saved Worker.
     */
    public Worker save(Worker worker) {
        boolean isNew = worker.getId() == null;

        if (!isNew) {
            Worker existing = workerRepository.findById(worker.getId()).orElse(null);
            if (existing != null) {
                String submitted = worker.getPassword();
                if (submitted == null || submitted.trim().isEmpty()) {
                    worker.setPassword(existing.getPassword());
                } else if (!submitted.startsWith("$2")) {
                    worker.setPassword(passwordEncoder.encode(submitted.trim()));
                }
            }
        } else {
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

    /**
     * Permanently removes a worker from the system.
     * @param id ID to delete.
     */
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
     * Authenticates a worker by username and password.
     *
     * @param username Login username.
     * @param password Plain-text password to verify.
     * @return Optional containing the Worker if authentication succeeds.
     */
    public Optional<Worker> login(String username, String password) {
        Optional<Worker> workerOpt = workerRepository.findByUsername(username)
                .filter(Worker::isActive)
                .filter(w -> passwordEncoder.matches(password, w.getPassword()));

        if (workerOpt.isPresent()) {
            activityLogService.logActivity(
                    "INICIAR_SESION",
                    "Inicio de sesión exitoso: " + username,
                    username,
                    "WORKER",
                    workerOpt.get().getId());
        } else {
            activityLogService.logActivity(
                    "INICIAR_SESION_FALLIDO",
                    "Intento de inicio de sesión fallido: " + username,
                    "Sistema",
                    "WORKER",
                    null);
        }

        return workerOpt;
    }
}


