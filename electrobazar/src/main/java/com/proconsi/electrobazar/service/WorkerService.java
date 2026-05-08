package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.WorkerRepository;
import com.proconsi.electrobazar.repository.specification.WorkerSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final com.proconsi.electrobazar.repository.SaleRepository saleRepository;

    /**
     * Retrieves workers with optional filtering (search query, role, and status).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<Worker> getFilteredWorkers(String search, Long roleId, Boolean active, Pageable pageable) {
        Specification<Worker> spec = WorkerSpecification.filterWorkers(search, roleId, active);
        return workerRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<com.proconsi.electrobazar.dto.AdminWorkerProjection> findAdminListing(String search, Long roleId, Boolean active, Pageable pageable) {
        Specification<Worker> spec = WorkerSpecification.filterWorkers(search, roleId, active);
        return workerRepository.findAdminListing(spec, pageable);
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

            String submittedPin = worker.getPinCode();
            if (submittedPin != null && !submittedPin.trim().isEmpty() && !submittedPin.startsWith("$2")) {
                existing.setPinCode(passwordEncoder.encode(submittedPin.trim()));
            }
            
            workerToSave = existing;
        } else {
            if (worker.getPassword() != null && !worker.getPassword().trim().isEmpty()) {
                worker.setPassword(passwordEncoder.encode(worker.getPassword().trim()));
            }
            if (worker.getPinCode() != null && !worker.getPinCode().trim().isEmpty()) {
                worker.setPinCode(passwordEncoder.encode(worker.getPinCode().trim()));
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
                "Sistema",
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
            if ("root".equalsIgnoreCase(w.getUsername())) {
                throw new RuntimeException("No se puede eliminar el usuario root.");
            }

            // Check if worker has sales
            if (saleRepository.existsByWorkerId(id)) {
                log.info("Worker {} has sales. Deactivating instead of deleting.", w.getUsername());
                w.setActive(false);
                workerRepository.save(w);
                activityLogService.logActivity(
                        "DESACTIVAR_TRABAJADOR",
                        "Trabajador con ventas desactivado (no se puede borrar): " + w.getUsername(),
                        "Sistema",
                        "WORKER",
                        id);
                return;
            }

            workerRepository.deleteById(id);
            activityLogService.logActivity(
                    "ELIMINAR_TRABAJADOR",
                    "Trabajador eliminado definitivamente: " + w.getUsername(),
                    "Sistema",
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

        String subject = "Código de recuperación de contraseña - Electrobazar";
        
        String body = "<!DOCTYPE html>" +
                      "<html>" +
                      "<head>" +
                      "<style>" +
                      "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f7f6; margin: 0; padding: 0; }" +
                      ".container { max-width: 600px; margin: 40px auto; background: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.05); }" +
                      ".header { text-align: center; border-bottom: 2px solid #e2e8f0; padding-bottom: 20px; margin-bottom: 20px; }" +
                      ".header h2 { color: #1e293b; margin: 0; font-size: 24px; }" +
                      ".content { color: #475569; font-size: 16px; line-height: 1.6; }" +
                      ".pin-box { background: #f8fafc; border: 2px dashed #cbd5e1; border-radius: 8px; padding: 20px; text-align: center; margin: 30px 0; }" +
                      ".pin-code { font-size: 32px; font-weight: bold; color: #0284c7; letter-spacing: 5px; }" +
                      ".footer { margin-top: 30px; font-size: 13px; color: #94a3b8; text-align: center; border-top: 1px solid #e2e8f0; padding-top: 20px; }" +
                      "</style>" +
                      "</head>" +
                      "<body>" +
                      "<div class='container'>" +
                      "  <div class='header'>" +
                      "    <h2>Restablecimiento de Contraseña</h2>" +
                      "  </div>" +
                      "  <div class='content'>" +
                      "    <p>Hola <strong>" + worker.getUsername() + "</strong>,</p>" +
                      "    <p>Hemos recibido una solicitud para restablecer la contraseña de tu cuenta. Tu código de verificación es:</p>" +
                      "    <div class='pin-box'>" +
                      "      <div class='pin-code'>" + pin + "</div>" +
                      "    </div>" +
                      "    <p><em>Este código caducará en 15 minutos.</em></p>" +
                      "    <p>Si no has solicitado este cambio, puedes ignorar este correo. Tu contraseña actual seguirá siendo válida.</p>" +
                      "  </div>" +
                      "  <div class='footer'>" +
                      "    <p>&copy; " + java.time.Year.now().getValue() + " Electrobazar TPV. Todos los derechos reservados.</p>" +
                      "  </div>" +
                      "</div>" +
                      "</body>" +
                      "</html>";

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
