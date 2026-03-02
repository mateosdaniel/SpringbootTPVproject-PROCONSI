package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerApiRestController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<List<Customer>> getAll() {
        return ResponseEntity.ok(customerService.findAllActive());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Customer>> search(@RequestParam String query) {
        return ResponseEntity.ok(customerService.searchCustomers(query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.findById(id));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Customer customer) {
        try {
            // apply sensible defaults to avoid NPE or DB constraint violations
            if (customer.getType() == null) {
                customer.setType(Customer.CustomerType.INDIVIDUAL);
            }
            if (customer.getActive() == null) {
                customer.setActive(true);
            }

            // minimal server-side validation so callers realise why a request failed
            if (customer.getName() == null || customer.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre es obligatorio"));
            }
            if (customer.getType() == Customer.CustomerType.COMPANY &&
                    (customer.getTaxId() == null || customer.getTaxId().trim().isEmpty())) {
                return ResponseEntity.badRequest().body(Map.of("error", "El CIF es obligatorio para empresas"));
            }
            Customer saved = customerService.save(customer);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (DataIntegrityViolationException dive) {
            // likely a constraint violation such as duplicate taxId
            dive.printStackTrace();
            String msg = dive.getRootCause() != null ? dive.getRootCause().getMessage() : dive.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Violación de integridad: " + msg));
        } catch (Exception e) {
            // log error and return message to client
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al crear cliente: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Customer customer) {
        try {
            // ensure id and defaults
            customer.setId(id);
            if (customer.getType() == null) {
                customer.setType(Customer.CustomerType.INDIVIDUAL);
            }
            if (customer.getActive() == null) {
                customer.setActive(true);
            }
            Customer updated = customerService.update(id, customer);
            return ResponseEntity.ok(updated);
        } catch (DataIntegrityViolationException dive) {
            dive.printStackTrace();
            String msg = dive.getRootCause() != null ? dive.getRootCause().getMessage() : dive.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Violación de integridad: " + msg));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al actualizar cliente: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
