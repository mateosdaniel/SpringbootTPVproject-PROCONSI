package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.Category;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for complex {@link Category} filtering.
 * Allows dynamic building of search criteria based on user input from the admin panel.
 */
public class CategorySpecification {

    /**
     * Filters categories by name or description.
     * @param search The search token (partial match, case-insensitive).
     * @return A Specification object for use with JpaSpecificationExecutor.
     */
    public static Specification<Category> filterCategories(String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.toLowerCase().trim() + "%";
                Predicate namePredicate = cb.like(cb.lower(root.get("name")), searchPattern);
                Predicate descPredicate = cb.like(cb.lower(root.get("description")), searchPattern);
                predicates.add(cb.or(namePredicate, descPredicate));
            }

            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}


