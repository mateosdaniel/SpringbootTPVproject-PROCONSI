package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Customer.CustomerType;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class CustomerSpecification {

    public static Specification<Customer> filterCustomers(String search, CustomerType type, Boolean hasRecargo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("taxId")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)
                ));
            }

            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            if (hasRecargo != null) {
                predicates.add(cb.equal(root.get("hasRecargoEquivalencia"), hasRecargo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
