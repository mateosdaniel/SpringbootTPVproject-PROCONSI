package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.Product;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for advanced {@link Product} filtering.
 * Supports multi-parameter search, stock thresholds, and status filtering.
 */
public class ProductSpecification {

    /**
     * Dynamically builds a product filter based on multiple optional criteria.
     * 
     * @param search Keyword matching name, description, or exact ID.
     * @param category Exact name of the product category.
     * @param stockFilter "low" ( < 5 units ) or "normal" ( >= 5 units ).
     * @param active Only active, inactive, or all products if null.
     * @return Specification for the search request.
     */
    public static Specification<Product> filterProducts(String search, String category, String stockFilter,
            Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Optimization: Eager load category if results are requested (not just counting)
            if (Long.class != query.getResultType()) {
                root.fetch("category", JoinType.LEFT);
            }

            // 1. Global keyword search
            if (search != null && !search.trim().isEmpty()) {
                String searchParam = search.trim();
                String searchPattern = "%" + searchParam.toLowerCase() + "%";
                
                // Search in both Spanish and English names
                Predicate nameEsPredicate = cb.like(cb.lower(root.get("nameEs")), searchPattern);
                Predicate nameEnPredicate = cb.like(cb.lower(root.get("nameEn")), searchPattern);
                
                // Search in both Spanish and English descriptions
                Predicate descEsPredicate = cb.like(cb.lower(root.get("descriptionEs")), searchPattern);
                Predicate descEnPredicate = cb.like(cb.lower(root.get("descriptionEn")), searchPattern);

                try {
                    Long idSearch = Long.parseLong(searchParam);
                    Predicate idPredicate = cb.equal(root.get("id"), idSearch);
                    predicates.add(cb.or(nameEsPredicate, nameEnPredicate, descEsPredicate, descEnPredicate, idPredicate));
                } catch (NumberFormatException e) {
                    predicates.add(cb.or(nameEsPredicate, nameEnPredicate, descEsPredicate, descEnPredicate));
                }
            }

            // 2. Category classification
            if (category != null && !category.trim().isEmpty()) {
                // Check category name (Spanish) specifically
                predicates.add(cb.equal(root.get("category").get("nameEs"), category));
            }

            // 3. Stock levels threshold
            if (stockFilter != null && !stockFilter.trim().isEmpty()) {
                if ("low".equalsIgnoreCase(stockFilter)) {
                    predicates.add(cb.lessThan(root.get("stock"), 5));
                } else if ("normal".equalsIgnoreCase(stockFilter)) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("stock"), 5));
                }
            }

            // 4. Activation status
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            query.orderBy(cb.asc(root.get("nameEs")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}


