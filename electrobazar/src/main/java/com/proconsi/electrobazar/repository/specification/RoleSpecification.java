package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.Role;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class RoleSpecification {

    public static Specification<Role> filterRoles(String search, List<String> permissions) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }

            if (permissions != null && !permissions.isEmpty()) {
                for (String perm : permissions) {
                    predicates.add(cb.isMember(perm, root.get("permissions")));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
