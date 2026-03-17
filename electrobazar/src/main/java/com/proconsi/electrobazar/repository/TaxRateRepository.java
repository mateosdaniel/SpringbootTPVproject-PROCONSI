package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for {@link TaxRate} entities.
 * Manages fiscal VAT and Recargo de Equivalencia rates over time.
 */
@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    /**
     * Lists all current and active tax rates.
     */
    List<TaxRate> findByActiveTrue();

    /**
     * Finds tax rates scheduled to start after a specific date.
     */
    List<TaxRate> findByValidFromAfter(LocalDate date);

    /**
     * Finds rates with a specific description (excluding a given ID).
     * Useful for duplicate name validation during updates.
     */
    List<TaxRate> findByDescriptionAndIdNot(String description, Long id);

    /**
     * Finds active rates that started on a specific date.
     */
    List<TaxRate> findByValidFromAndActiveTrue(LocalDate date);
}


