package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.dto.AdminCashRegisterProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CashRegister} entities.
 * Manages the lifecycle of terminal shifts (opening, closing, and reconciliation).
 * Uses EntityGraphs to eagerly load associated worker entities and prevent N+1 queries.
 */
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;


@Repository
public interface CashRegisterRepository extends JpaRepository<CashRegister, Long>, JpaSpecificationExecutor<CashRegister> {

    @Query("SELECT r.id as id, r.openingTime as openingTime, r.closedAt as closedAt, " +
           "r.openingBalance as openingBalance, r.totalSales as totalSales, " +
           "r.closingBalance as closingBalance, r.difference as difference, " +
           "w.username as workerUsername " +
           "FROM CashRegister r LEFT JOIN r.worker w")
    org.springframework.data.domain.Slice<AdminCashRegisterProjection> findAdminListing(org.springframework.data.jpa.domain.Specification<CashRegister> spec, org.springframework.data.domain.Pageable pageable);


    /**
     * Slice-based search to avoid COUNT(*) on shift history.
     */


    @EntityGraph(attributePaths = { "worker" })
    Slice<CashRegister> findByClosedTrue(Pageable pageable);

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


    /**
     * Finds the currently open shift, if any.
     */
    @EntityGraph(attributePaths = { "worker" })
    Optional<CashRegister> findFirstByClosedFalseOrderByRegisterDateDesc();

    /**
     * Finds a shift by ID, eagerly fetching worker relations.
     */
    @EntityGraph(attributePaths = { "worker", "retainedByWorker" })
    Optional<CashRegister> findById(Long id);

    /**
     * Finds the most recently closed shift for balance carry-over calculations.
     */
    @EntityGraph(attributePaths = { "worker", "retainedByWorker" })
    Optional<CashRegister> findFirstByClosedTrueOrderByClosedAtDesc();
}
