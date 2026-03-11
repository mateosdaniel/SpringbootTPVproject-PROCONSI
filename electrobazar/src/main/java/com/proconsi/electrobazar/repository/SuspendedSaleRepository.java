package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.SuspendedSale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuspendedSaleRepository extends JpaRepository<SuspendedSale, Long> {

    /** All currently suspended sales, newest first. */
    @EntityGraph(attributePaths = { "lines", "lines.product", "worker" })
    List<SuspendedSale> findByStatusOrderByCreatedAtDesc(SuspendedSale.SuspendedSaleStatus status);

    /** Fetch a suspended sale with its lines and products eagerly to avoid N+1. */
    @EntityGraph(attributePaths = { "lines", "lines.product", "worker" })
    Optional<SuspendedSale> findById(Long id);
}
