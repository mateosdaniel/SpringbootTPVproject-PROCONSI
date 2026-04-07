package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.ActivityLog;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogSpecification {

    public static Specification<ActivityLog> filterLogs(String search, String action, String username) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("username")), pattern)
                ));
            }

            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }

            if (username != null && !username.isBlank()) {
                predicates.add(cb.equal(root.get("username"), username));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
