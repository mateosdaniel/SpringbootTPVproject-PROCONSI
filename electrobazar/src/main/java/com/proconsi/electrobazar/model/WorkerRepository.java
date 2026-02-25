package com.proconsi.electrobazar.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Optional<Worker> findByUsername(String username);
}
