package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.dto.AdminCustomerProjection;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.repository.specification.CustomerSpecification;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CustomerService;
import com.proconsi.electrobazar.util.NifCifValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link CustomerService}.
 * Handles business logic for customer lifecycle, including NIF/CIF validation
 * and default tariff assignment (MINORISTA).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final ActivityLogService activityLogService;
    private final TariffRepository tariffRepository;
    private final com.proconsi.electrobazar.repository.SaleRepository saleRepository;
    private final com.proconsi.electrobazar.repository.AbonoRepository abonoRepository;
    private final NifCifValidator nifCifValidator;

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<Customer> getFilteredCustomers(String search, Customer.CustomerType type, Boolean hasRecargo, Pageable pageable) {
        Specification<Customer> spec = CustomerSpecification.filterCustomers(search, type, hasRecargo);
        return customerRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Slice<AdminCustomerProjection> findAdminListing(String search, Customer.CustomerType type, Boolean hasRecargo, Pageable pageable) {
        Specification<Customer> spec = CustomerSpecification.filterCustomers(search, type, hasRecargo);
        return customerRepository.findAdminListing(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Customer> findAll(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAllActive() {
        return customerRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }

    @Override
    public Customer save(Customer customer) {
        if (customer.getTaxId() != null && !customer.getTaxId().trim().isEmpty()) {
            if (!nifCifValidator.isValid(customer.getTaxId())) {
                throw new IllegalArgumentException("The provided NIF/CIF or NIE is invalid.");
            }
            // Check for duplicates
            customerRepository.findByTaxId(customer.getTaxId().trim().toUpperCase())
                .ifPresent(c -> { throw new IllegalArgumentException("Ya existe un cliente con ese número de documento (NIF/CIF)"); });
        }
        
        if (customer.getIdDocumentNumber() != null && !customer.getIdDocumentNumber().trim().isEmpty()) {
            customerRepository.findByIdDocumentNumber(customer.getIdDocumentNumber().trim().toUpperCase())
                .ifPresent(c -> { throw new IllegalArgumentException("Ya existe un cliente con ese número de documento"); });
        }
        
        // Ensure required defaults as JPA columns may be non-nullable
        if (customer.getType() == null) {
            customer.setType(Customer.CustomerType.INDIVIDUAL);
        }
        if (customer.getActive() == null) {
            customer.setActive(true);
        }
        
        // Default to MINORISTA tariff if none is specified
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
                log.warn("Update rejected for customer ID {}: invalid taxId='{}'", id, updated.getTaxId());
                throw new IllegalArgumentException("The provided NIF/CIF or NIE is invalid.");
            }
            // Check for duplicates (excluding current customer)
            customerRepository.findByTaxId(updated.getTaxId().trim().toUpperCase())
                .filter(c -> !c.getId().equals(id))
                .ifPresent(c -> { throw new IllegalArgumentException("Ya existe un cliente con ese número de documento (NIF/CIF)"); });
        }

        if (updated.getIdDocumentNumber() != null && !updated.getIdDocumentNumber().trim().isEmpty()) {
            customerRepository.findByIdDocumentNumber(updated.getIdDocumentNumber().trim().toUpperCase())
                .filter(c -> !c.getId().equals(id))
                .ifPresent(c -> { throw new IllegalArgumentException("Ya existe un cliente con ese número de documento"); });
        }
        Customer existing = findById(id);

        existing.setName(updated.getName());
        existing.setTaxId(updated.getTaxId());
        existing.setIdDocumentType(updated.getIdDocumentType());
        existing.setIdDocumentNumber(updated.getIdDocumentNumber());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setAddress(updated.getAddress());
        existing.setCity(updated.getCity());
        existing.setPostalCode(updated.getPostalCode());
        existing.setType(updated.getType());
        existing.setActive(updated.getActive());
        existing.setHasRecargoEquivalencia(updated.getHasRecargoEquivalencia() != null ? updated.getHasRecargoEquivalencia() : false);

        if (updated.getTariff() != null && updated.getTariff().getId() != null) {
            tariffRepository.findById(updated.getTariff().getId()).ifPresent(existing::setTariff);
        } else if (existing.getTariff() == null) {
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
    public void delete(Long id, boolean forceDeactivate) {
        Customer customer = findById(id);
        
        boolean hasSales = saleRepository.existsByCustomerId(id);
        boolean hasAbonos = abonoRepository.existsByClienteId(id);

        if (hasSales || hasAbonos) {
            if (forceDeactivate) {
                customer.setActive(false);
                customerRepository.save(customer);
                activityLogService.logActivity(
                        "DESACTIVAR_CLIENTE",
                        "Cliente con historial desactivado: " + customer.getName(),
                        "Admin",
                        "CUSTOMER",
                        customer.getId());
            } else {
                throw new IllegalStateException("HAS_SALES");
            }
        } else {
            customerRepository.delete(customer);
            activityLogService.logActivity(
                    "ELIMINAR_CLIENTE",
                    "Cliente eliminado permanentemente: " + customer.getName(),
                    "Admin",
                    "CUSTOMER",
                    id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return customerRepository.findByActiveTrueOrderByNameAsc();
        }
        return customerRepository.searchActive(query.trim());
    }
}
