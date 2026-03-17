package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.SuspendedSale;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link SuspendedSale} entities.
 * Manages "parked" or incomplete sales intended for later retrieval.
 */
@Repository
public interface SuspendedSaleRepository extends JpaRepository<SuspendedSale, Long> {

    /**
     * Retrieves all sales currently in a specific status (usually SUSPENDED).
     * eager-loads lines and products to optimize the "Resumption" UI.
     */
    @EntityGraph(attributePaths = { "lines", "lines.product", "worker" })
    List<SuspendedSale> findByStatusOrderByCreatedAtDesc(SuspendedSale.SuspendedSaleStatus status);

    /**
     * Fetches a suspended sale by ID with its full hierarchy.
     */
    @Override
    @EntityGraph(attributePaths = { "lines", "lines.product", "worker" })
    Optional<SuspendedSale> findById(Long id);
}


