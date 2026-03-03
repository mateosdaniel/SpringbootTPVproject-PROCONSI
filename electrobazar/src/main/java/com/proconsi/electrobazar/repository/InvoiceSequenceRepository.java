package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {

    /**
     * Finds the sequence row for the given serie+year and acquires a
     * PESSIMISTIC_WRITE lock
     * to prevent concurrent threads from reading the same lastNumber before it is
     * incremented.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InvoiceSequence s WHERE s.serie = :serie AND s.year = :year")
    Optional<InvoiceSequence> findBySerieAndYearForUpdate(
            @Param("serie") String serie,
            @Param("year") int year);
}
