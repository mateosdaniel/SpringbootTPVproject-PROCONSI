package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * Interface defining operations for customer management.
 */
public interface CustomerService {

    /**
     * Retrieves all customers with pagination support.
     * @param pageable Pagination and sorting criteria.
     * @return A page of customers.
     */
    Page<Customer> findAll(Pageable pageable);

    /**
     * Retrieves all customers in the system.
     * @return A list of all Customer entities.
     */
    List<Customer> findAll();

    /**
     * Retrieves only active customers.
     * @return A list of active customers.
     */
    List<Customer> findAllActive();

    /**
     * Finds a specific customer by ID.
     * @param id The primary key.
     * @return The found Customer entity.
     */
    Customer findById(Long id);

    /**
     * Persists a new customer record.
     * @param customer The entity data.
     * @return The saved Customer.
     */
    Customer save(Customer customer);

    /**
     * Updates an existing customer's details.
     * @param id       The ID of the customer to update.
     * @param customer The updated data.
     * @return The updated Customer.
     */
    Customer update(Long id, Customer customer);

    /**
     * Performs a soft delete (sets active = false).
     * @param id The ID of the customer to deactivate.
     */
    void delete(Long id);

    /**
     * Retrieves customers with optional filtering (search query, type, and surcharge status).
     */
    Page<Customer> getFilteredCustomers(String search, com.proconsi.electrobazar.model.Customer.CustomerType type, Boolean hasRecargo, Pageable pageable);

    /**
     * Searches for customers by name, tax ID, or email.
     */
    List<Customer> searchCustomers(String query);
}
