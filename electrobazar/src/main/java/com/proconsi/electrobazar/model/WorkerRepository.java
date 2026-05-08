package com.proconsi.electrobazar.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.proconsi.electrobazar.dto.AdminWorkerProjection;
import org.springframework.data.jpa.repository.Query;

public interface WorkerRepository extends JpaRepository<Worker, Long>, JpaSpecificationExecutor<Worker> {

    /**
     * Slice-based search to avoid COUNT(*) on worker list.
     */
    Slice<Worker> findSliceBy(Specification<Worker> spec, Pageable pageable);

    @Query("SELECT w.id as id, w.username as username, w.active as active, r.id as roleId, r.name as roleName, " +
           "CASE WHEN EXISTS (SELECT s FROM Sale s WHERE s.worker.id = w.id) THEN true ELSE false END as hasSales " +
           "FROM Worker w LEFT JOIN w.role r")
    Slice<AdminWorkerProjection> findAdminListing(Specification<Worker> spec, Pageable pageable);

    Optional<Worker> findByUsername(String username);
    Optional<Worker> findByEmail(String email);
    long countByRole_Id(Long roleId);
    List<Worker> findByRole_Id(Long roleId);
}
