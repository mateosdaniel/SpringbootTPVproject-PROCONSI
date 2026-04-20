package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Customer} entities.
 * handles customer data management, tax ID (CIF/NIF) lookups, and search functionality.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

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
     * Finds a customer by its identity document number (DNI/NIE/Passport).
     */
    Optional<Customer> findByIdDocumentNumber(String idDocumentNumber);

    /**
     * Searches for active customers whose name or tax ID matches the query.
     */
    @org.springframework.data.jpa.repository.Query("SELECT c FROM Customer c WHERE (c.name LIKE %:query% OR c.taxId LIKE %:query%) AND c.active = true ORDER BY c.name ASC")
    List<Customer> searchActive(@org.springframework.data.repository.query.Param("query") String query);

    /**
     * Counts active customers without an explicitly assigned tariff.
     */
    long countByTariffIsNullAndActiveTrue();
}
