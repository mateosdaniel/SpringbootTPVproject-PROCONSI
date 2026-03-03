package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAllActive() {
        return customerRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Customer findById(Long id) {
        return customerRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con id: " + id));
    }

    @Override
    public Customer save(Customer customer) {
        // ensure required defaults as JPA column is not nullable
        if (customer.getType() == null) {
            customer.setType(Customer.CustomerType.INDIVIDUAL);
        }
        if (customer.getActive() == null) {
            customer.setActive(true);
        }

        Customer saved = customerRepository.save(customer);
        activityLogService.logActivity(
                "CREAR_CLIENTE",
                "Nuevo cliente registrado: " + saved.getName(),
                "Admin",
                "CUSTOMER",
                saved.getId());
        return saved;
    }

    @Override
    public Customer update(Long id, Customer updated) {
        Customer existing = findById(id);

        existing.setName(updated.getName());
        existing.setTaxId(updated.getTaxId());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setAddress(updated.getAddress());
        existing.setCity(updated.getCity());
        existing.setPostalCode(updated.getPostalCode());
        existing.setType(updated.getType());
        existing.setActive(updated.getActive());
        // Persist the Recargo de Equivalencia flag
        existing.setHasRecargoEquivalencia(
                updated.getHasRecargoEquivalencia() != null ? updated.getHasRecargoEquivalencia() : false);

        Customer saved = customerRepository.save(existing);
        activityLogService.logActivity(
                "ACTUALIZAR_CLIENTE",
                "Cliente actualizado: " + saved.getName(),
                "Admin",
                "CUSTOMER",
                saved.getId());
        return saved;
    }

    @Override
    public void delete(Long id) {
        Customer customer = findById(id);
        customer.setActive(false);
        customerRepository.save(customer);

        activityLogService.logActivity(
                "ELIMINAR_CLIENTE",
                "Cliente eliminado/desactivado: " + customer.getName(),
                "Admin",
                "CUSTOMER",
                customer.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return customerRepository.findByActiveTrueOrderByNameAsc();
        }
        return customerRepository.findByNameContainingIgnoreCaseOrTaxIdContainingIgnoreCaseAndActiveTrue(query, query);
    }
}
