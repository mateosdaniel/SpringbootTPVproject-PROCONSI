package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.Worker;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class WorkerSpecification {

    public static Specification<Worker> filterWorkers(String search, Long roleId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + search.toLowerCase() + "%"));
            }

            if (roleId != null) {
                predicates.add(cb.equal(root.get("role").get("id"), roleId));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
