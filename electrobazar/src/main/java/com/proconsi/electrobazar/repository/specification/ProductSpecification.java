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
                String[] tokens = search.trim().toLowerCase().split("\\s+");

                if (tokens.length == 1) {
                    // Single token: keep original broad search (name + description + ID)
                    String searchPattern = "%" + tokens[0] + "%";
                    Predicate nameEsPredicate = cb.like(cb.lower(root.get("nameEs")), searchPattern);
                    Predicate nameEnPredicate = cb.like(cb.lower(root.get("nameEn")), searchPattern);
                    Predicate descEsPredicate = cb.like(cb.lower(root.get("descriptionEs")), searchPattern);
                    Predicate descEnPredicate = cb.like(cb.lower(root.get("descriptionEn")), searchPattern);

                    try {
                        Long idSearch = Long.parseLong(tokens[0]);
                        Predicate idPredicate = cb.equal(root.get("id"), idSearch);
                        predicates.add(cb.or(nameEsPredicate, nameEnPredicate, descEsPredicate, descEnPredicate, idPredicate));
                    } catch (NumberFormatException e) {
                        predicates.add(cb.or(nameEsPredicate, nameEnPredicate, descEsPredicate, descEnPredicate));
                    }
                } else {
                    // Multi-token: each token must appear in at least one of the name fields.
                    // All tokens are AND'd so "Pro 1" requires both "pro" AND "1" to be present.
                    // Order is irrelevant: "1 Pro" also matches "Producto de prueba 1".
                    for (String token : tokens) {
                        String pattern = "%" + token + "%";
                        Predicate nameEsMatch = cb.like(cb.lower(root.get("nameEs")), pattern);
                        Predicate nameEnMatch = cb.like(cb.lower(root.get("nameEn")), pattern);
                        // Each token must appear in at least one name field
                        predicates.add(cb.or(nameEsMatch, nameEnMatch));
                    }
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


