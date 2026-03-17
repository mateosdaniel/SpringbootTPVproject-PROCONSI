package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing Customers.
 * Handles CRM-like operations, including search, details, and purchase history.
 */
@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerApiRestController {

    private final CustomerService customerService;
    private final TariffRepository tariffRepository;
    private final SaleRepository saleRepository;

    /**
     * Retrieves all active customers.
     * @return List of {@link Customer} entities.
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getAll() {
        return ResponseEntity.ok(customerService.findAllActive());
    }

    /**
     * Searches for customers by name, tax ID, or email.
     * @param query Search string.
     * @return List of matching customers.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Customer>> search(@RequestParam String query) {
        return ResponseEntity.ok(customerService.searchCustomers(query));
    }

    /**
     * Retrieves a single customer's details.
     * @param id The customer ID.
     * @return The requested {@link Customer}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.findById(id));
    }

    /**
     * Registers a new customer in the system.
     * @param body Map of customer properties.
     * @return 201 Created with the saved {@link Customer}.
     */
    @PostMapping
    public ResponseEntity<Customer> create(@RequestBody Map<String, Object> body) {
        Customer customer = buildCustomerFromBody(body, new Customer());

        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (customer.getType() == Customer.CustomerType.COMPANY &&
                (customer.getTaxId() == null || customer.getTaxId().trim().isEmpty())) {
            throw new IllegalArgumentException("El CIF es obligatorio para empresas");
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.save(customer));
    }

    /**
     * Updates an existing customer's information.
     * @param id The customer ID.
     * @param body Map with new customer properties.
     * @return 200 OK with the updated entity.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.info("Customer update request for id={}: {}", id, body);
        Customer existing = customerService.findById(id);
        Customer updated = buildCustomerFromBody(body, existing);
        updated.setId(id);
        return ResponseEntity.ok(customerService.update(id, updated));
    }

    /**
     * Deactivates a customer (Soft Delete).
     * @param id The customer ID.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the complete purchase history for a specific customer.
     * @param id The customer ID.
     * @return A list of sales with itemized lines formatted for UI display.
     */
    @GetMapping("/{id}/sales")
    public ResponseEntity<List<Map<String, Object>>> getSalesByCustomer(@PathVariable Long id) {
        List<Sale> sales = saleRepository.findByCustomerIdOrderByCreatedAtDesc(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Sale s : sales) {
            Map<String, Object> saleMap = new LinkedHashMap<>();
            saleMap.put("id", s.getId());
            saleMap.put("createdAt", s.getCreatedAt());
            saleMap.put("paymentMethod", s.getPaymentMethod());
            saleMap.put("totalAmount", s.getTotalAmount());
            saleMap.put("status", s.getStatus());
            saleMap.put("appliedTariff", s.getAppliedTariff());
            String workerName = (s.getWorker() != null) ? s.getWorker().getUsername() : null;
            saleMap.put("workerName", workerName);
            List<Map<String, Object>> lines = new ArrayList<>();
            for (SaleLine sl : s.getLines()) {
                Map<String, Object> lineMap = new LinkedHashMap<>();
                lineMap.put("productName", sl.getProduct() != null ? sl.getProduct().getName() : "—");
                lineMap.put("quantity", sl.getQuantity());
                lineMap.put("unitPrice", sl.getUnitPrice());
                lineMap.put("subtotal", sl.getSubtotal());
                lines.add(lineMap);
            }
            saleMap.put("lines", lines);
            result.add(saleMap);
        }
        return ResponseEntity.ok(result);
    }

    private Customer buildCustomerFromBody(Map<String, Object> body, Customer customer) {
        if (body.containsKey("name"))
            customer.setName((String) body.get("name"));
        if (body.containsKey("taxId"))
            customer.setTaxId((String) body.get("taxId"));
        if (body.containsKey("email"))
            customer.setEmail((String) body.get("email"));
        if (body.containsKey("phone"))
            customer.setPhone((String) body.get("phone"));
        if (body.containsKey("address"))
            customer.setAddress((String) body.get("address"));
        if (body.containsKey("city"))
            customer.setCity((String) body.get("city"));
        if (body.containsKey("postalCode"))
            customer.setPostalCode((String) body.get("postalCode"));
        if (body.containsKey("type")) {
            try {
                customer.setType(Customer.CustomerType.valueOf((String) body.get("type")));
            } catch (Exception ignored) {
                customer.setType(Customer.CustomerType.INDIVIDUAL);
            }
        }
        if (customer.getType() == null)
            customer.setType(Customer.CustomerType.INDIVIDUAL);

        if (body.containsKey("active"))
            customer.setActive(Boolean.TRUE.equals(body.get("active")));
        if (customer.getActive() == null)
            customer.setActive(true);

        if (body.containsKey("hasRecargoEquivalencia"))
            customer.setHasRecargoEquivalencia(Boolean.TRUE.equals(body.get("hasRecargoEquivalencia")));
        if (customer.getHasRecargoEquivalencia() == null)
            customer.setHasRecargoEquivalencia(false);

        Tariff tariff = null;
        if (body.containsKey("tariffId") && body.get("tariffId") != null) {
            Long tariffId = Long.parseLong(body.get("tariffId").toString());
            tariff = tariffRepository.findById(tariffId).orElse(null);
        } else if (body.containsKey("tariffName") && body.get("tariffName") != null) {
            tariff = tariffRepository.findByName((String) body.get("tariffName")).orElse(null);
        }
        if (tariff != null)
            customer.setTariff(tariff);

        return customer;
    }
}
