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
        return workerRepository.save(worker);
    }

    public void deleteById(Long id) {
        workerRepository.deleteById(id);
    }

    public Optional<Worker> login(String username, String password) {
        return workerRepository.findByUsername(username)
                .filter(Worker::isActive)
                .filter(w -> w.getPassword().equals(password));
    }
}
