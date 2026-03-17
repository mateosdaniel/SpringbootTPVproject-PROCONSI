package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link InvoiceSequence} entities.
 * CRITICAL: Manages atomic sequence counters for Invoices, Tickets, and Returns.
 * Ensures no gaps or duplicates in official documentation.
 */
@Repository
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {

    /**
     * Acquires a PESSIMISTIC_WRITE lock on a sequence row for a specific series and year.
     * This prevents race conditions where two threads might read the same lastNumber 
     * before it is incremented during document generation.
     * 
     * @param serie The document series (e.g., "F", "T", "D").
     * @param year The fiscal year.
     * @return The locked sequence row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.serie = :serie AND s.year = :year")
    Optional<InvoiceSequence> findBySerieAndYearForUpdate(
            @Param("serie") String serie,
            @Param("year") int year);
}


