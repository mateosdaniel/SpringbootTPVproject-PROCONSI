package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.model.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final ActivityLogService activityLogService;

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
            Worker existing = workerRepository.findById(worker.getId()).orElse(null);
            if (existing != null) {
                if (worker.getPassword() == null || worker.getPassword().trim().isEmpty()) {
                    worker.setPassword(existing.getPassword());
                }
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

    public Optional<Worker> login(String username, String password) {
        Optional<Worker> workerOpt = workerRepository.findByUsername(username)
                .filter(Worker::isActive)
                .filter(w -> w.getPassword().equals(password));

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
