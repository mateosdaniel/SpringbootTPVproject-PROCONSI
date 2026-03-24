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
                Predicate nameEsPredicate = cb.like(cb.lower(root.get("nameEs")), searchPattern);
                Predicate descEsPredicate = cb.like(cb.lower(root.get("descriptionEs")), searchPattern);
                predicates.add(cb.or(nameEsPredicate, descEsPredicate));
            }

            query.orderBy(cb.asc(root.get("nameEs")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}


