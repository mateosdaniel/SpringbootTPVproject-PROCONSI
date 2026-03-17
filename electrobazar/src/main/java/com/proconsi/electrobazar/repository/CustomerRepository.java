package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Customer} entities.
 * handles customer data management, tax ID (CIF/NIF) lookups, and search functionality.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Lists all active customers ordered alphabetically.
     */
    List<Customer> findByActiveTrueOrderByNameAsc();

    /**
     * Finds an active customer by its unique ID.
     */
    Optional<Customer> findByIdAndActiveTrue(Long id);

    /**
     * Finds a customer by its unique tax identifier (CIF/NIF/NIE).
     */
    Optional<Customer> findByTaxId(String taxId);

    /**
     * Searches for active customers whose name or tax ID matches the query.
     */
    List<Customer> findByNameContainingIgnoreCaseOrTaxIdContainingIgnoreCaseAndActiveTrue(String name, String taxId);
}
