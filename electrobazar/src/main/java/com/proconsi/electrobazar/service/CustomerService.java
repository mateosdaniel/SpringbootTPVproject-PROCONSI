package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Customer;
import java.util.List;

/**
 * Interface defining operations for customer management.
 * Supports CRUD, search, and activity filtering.
 */
public interface CustomerService {

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
     * Searches for customers by name, tax ID, or email.
     * @param query The search term.
     * @return A list of matching customers.
     */
    List<Customer> searchCustomers(String query);
}
