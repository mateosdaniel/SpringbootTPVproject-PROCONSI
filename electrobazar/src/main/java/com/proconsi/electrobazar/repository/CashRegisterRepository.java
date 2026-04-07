package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.CashRegister;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CashRegister} entities.
 * Manages the lifecycle of terminal shifts (opening, closing, and reconciliation).
 * Uses EntityGraphs to eagerly load associated worker entities and prevent N+1 queries.
 */
@Repository
public interface CashRegisterRepository extends JpaRepository<CashRegister, Long>, JpaSpecificationExecutor<CashRegister> {

    /**
     * Finds a closed shift for a specific date.
     */
    @EntityGraph(attributePaths = { "worker" })
    Optional<CashRegister> findByRegisterDateAndClosedTrue(LocalDate registerDate);

    /**
     * Lists all closed shifts ordered by date (most recent first).
     */
    @EntityGraph(attributePaths = { "worker" })
    List<CashRegister> findByClosedTrueOrderByRegisterDateDesc();

    @EntityGraph(attributePaths = { "worker" })
    org.springframework.data.domain.Page<CashRegister> findByClosedTrue(org.springframework.data.domain.Pageable pageable);

    /**
     * Finds the currently open shift, if any.
     */
    @EntityGraph(attributePaths = { "worker" })
    Optional<CashRegister> findFirstByClosedFalseOrderByRegisterDateDesc();

    /**
     * Finds the most recently closed shift for balance carry-over calculations.
     */
    @EntityGraph(attributePaths = { "worker", "retainedByWorker" })
    Optional<CashRegister> findFirstByClosedTrueOrderByClosedAtDesc();
}
