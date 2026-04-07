package com.proconsi.electrobazar.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long>, JpaSpecificationExecutor<Worker> {
    Optional<Worker> findByUsername(String username);
    Optional<Worker> findByEmail(String email);
    long countByRole_Id(Long roleId);
}
