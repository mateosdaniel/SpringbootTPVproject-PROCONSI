package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.WorkerRepository;
import com.proconsi.electrobazar.repository.specification.WorkerSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing system workers (users).
 * Handles authentication, password hashing, and user CRUD.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final com.proconsi.electrobazar.repository.RoleRepository roleRepository;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /**
     * Retrieves workers with optional filtering (search query, role, and status).
     */
    @Transactional(readOnly = true)
    public Page<Worker> getFilteredWorkers(String search, Long roleId, Boolean active, Pageable pageable) {
        Specification<Worker> spec = WorkerSpecification.filterWorkers(search, roleId, active);
        return workerRepository.findAll(spec, pageable);
    }

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

    public Optional<Worker> findByUsername(String username) {
        return workerRepository.findByUsername(username);
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
        final Worker workerToSave;
        boolean isNew = worker.getId() == null;

        if (!isNew) {
            Worker existing = workerRepository.findById(worker.getId())
                .orElseThrow(() -> new RuntimeException("Worker not found for ID: " + worker.getId()));
            
            // Merge fields to avoid NULLing out fields not sent in the JSON (like email, phone)
            existing.setUsername(worker.getUsername());
            existing.setActive(worker.isActive());
            
            // Assign role properly from DB if provided
            if (worker.getRole() != null && worker.getRole().getId() != null) {
                existing.setRole(roleRepository.findById(worker.getRole().getId()).orElse(null));
            } else {
                existing.setRole(null);
            }
            
            String submitted = worker.getPassword();
            if (submitted != null && !submitted.trim().isEmpty() && !submitted.startsWith("$2")) {
                existing.setPassword(passwordEncoder.encode(submitted.trim()));
            }
            // If submitted is null/empty, we keep the existing password (already in existing)
            
            workerToSave = existing;
        } else {
            if (worker.getPassword() != null && !worker.getPassword().trim().isEmpty()) {
                worker.setPassword(passwordEncoder.encode(worker.getPassword().trim()));
            }
            // Handle role for new worker
            if (worker.getRole() != null && worker.getRole().getId() != null) {
                worker.setRole(roleRepository.findById(worker.getRole().getId()).orElse(null));
            }
            workerToSave = worker;
        }

        Worker saved = workerRepository.save(workerToSave);

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

    /**
     * Initiates the password reset process by generating a 6-digit PIN 
     * and sending it to the worker's email.
     * 
     * @param email The registered worker's email.
     * @return True if worker exists and email was sent.
     */
    public boolean initiatePasswordReset(String email) {
        log.info("Requesting password reset for email: {}", email);
        Optional<Worker> workerOpt = workerRepository.findByEmail(email);
        if (workerOpt.isEmpty()) {
            log.warn("Password reset requested for unregistered email: {}", email);
            return false;
        }

        Worker worker = workerOpt.get();
        String pin = String.format("%06d", new java.util.Random().nextInt(999999));
        
        worker.setResetPin(pin);
        worker.setResetPinExpiration(java.time.LocalDateTime.now().plusMinutes(15));
        workerRepository.save(worker);

        String subject = "Código de recuperación de contraseña";
        String body = "Hola " + worker.getUsername() + ",\n\n" +
                      "Has solicitado restablecer tu contraseña. Tu código de recuperación es:\n\n" +
                      pin + "\n\n" +
                      "Este código caducará en 15 minutos.\n\n" +
                      "Si no has solicitado este cambio, por favor ignora este correo.";

        emailService.sendEmail(email, subject, body);
        return true;
    }

    /**
     * Completes the password reset by verifying the PIN and updating the password.
     * 
     * @param email The worker's email.
     * @param pin The 6-digit PIN received.
     * @param newPassword The plain-text new password.
     * @return True if reset was successful.
     */
    public boolean resetPassword(String email, String pin, String newPassword) {
        Optional<Worker> workerOpt = workerRepository.findByEmail(email);
        if (workerOpt.isEmpty()) return false;

        Worker worker = workerOpt.get();
        if (worker.getResetPin() == null || !worker.getResetPin().equals(pin)) return false;
        if (worker.getResetPinExpiration() == null || worker.getResetPinExpiration().isBefore(java.time.LocalDateTime.now())) return false;

        worker.setPassword(passwordEncoder.encode(newPassword.trim()));
        worker.setResetPin(null);
        worker.setResetPinExpiration(null);
        workerRepository.save(worker);

        activityLogService.logActivity(
                "RECUPERAR_CONTRASEÑA",
                "Contraseña restablecida correctamente vía email: " + email,
                worker.getUsername(),
                "WORKER",
                worker.getId());

        return true;
    }
}
