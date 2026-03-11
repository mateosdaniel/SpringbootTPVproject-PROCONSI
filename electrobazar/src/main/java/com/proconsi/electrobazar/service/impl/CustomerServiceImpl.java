package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.proconsi.electrobazar.util.NifCifValidator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final ActivityLogService activityLogService;
    private final TariffRepository tariffRepository;
    private final NifCifValidator nifCifValidator;

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
        if (customer.getTaxId() != null && !customer.getTaxId().trim().isEmpty()) {
            if (!nifCifValidator.isValid(customer.getTaxId())) {
                throw new IllegalArgumentException("El NIF/CIF o NIE introducido no es válido.");
            }
        }
        // ensure required defaults as JPA column is not nullable
        if (customer.getType() == null) {
            customer.setType(Customer.CustomerType.INDIVIDUAL);
        }
        if (customer.getActive() == null) {
            customer.setActive(true);
        }
        // Default to MINORISTA tariff if none is set
        if (customer.getTariff() == null) {
            tariffRepository.findByName(Tariff.MINORISTA).ifPresent(customer::setTariff);
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
        if (updated.getTaxId() != null && !updated.getTaxId().trim().isEmpty()) {
            if (!nifCifValidator.isValid(updated.getTaxId())) {
                log.warn("Customer update id={} rejected: invalid taxId='{}'", id, updated.getTaxId());
                throw new IllegalArgumentException("El NIF/CIF o NIE introducido no es válido.");
            }
        }
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

        // Update tariff – resolve by id from the updated object
        if (updated.getTariff() != null && updated.getTariff().getId() != null) {
            tariffRepository.findById(updated.getTariff().getId()).ifPresent(existing::setTariff);
        } else if (existing.getTariff() == null) {
            // Ensure MINORISTA is always set
            tariffRepository.findByName(Tariff.MINORISTA).ifPresent(existing::setTariff);
        }

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
