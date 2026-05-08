package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.dto.AdminCustomerProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Customer} entities.
 * handles customer data management, tax ID (CIF/NIF) lookups, and search functionality.
 */


@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    @Query("SELECT c.id as id, c.name as name, c.taxId as taxId, c.email as email, c.phone as phone, " +
           "c.city as city, c.type as type, c.hasRecargoEquivalencia as hasRecargoEquivalencia, " +
           "t.id as tariffId, t.name as tariffName, t.color as tariffColor, " +
           "c.idDocumentType as idDocumentType, c.idDocumentNumber as idDocumentNumber, " +
           "c.active as active " +
           "FROM Customer c LEFT JOIN c.tariff t")
    org.springframework.data.domain.Slice<AdminCustomerProjection> findAdminListing(org.springframework.data.jpa.domain.Specification<Customer> spec, org.springframework.data.domain.Pageable pageable);


    /**
     * Slice-based search for customers to avoid COUNT(*).
     */


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
