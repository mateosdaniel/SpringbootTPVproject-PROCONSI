package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Tariff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Tariff} entities.
 * Manages different pricing strategies and their adoption among customers.
 */
@Repository
public interface TariffRepository extends JpaRepository<Tariff, Long> {

    /**
     * Finds a tariff by its unique name.
     */
    Optional<Tariff> findByName(String name);

    /**
     * Lists all active tariffs ordered by name.
     */
    List<Tariff> findByActiveTrueOrderByNameAsc();

    /**
     * Counts the number of active customers assigned to each tariff.
     * Returns a list of [Tariff ID, Customer Count].
     */
    @Query("SELECT t.id, COUNT(c) FROM Customer c JOIN c.tariff t WHERE c.active = true GROUP BY t.id")
    List<Object[]> countCustomersPerTariff();
}


