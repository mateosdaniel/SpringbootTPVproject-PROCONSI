package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.ProductPrice;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProductPriceSpecification {

    public static Specification<ProductPrice> filterFuturePrices(String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Only future prices
            predicates.add(cb.greaterThan(root.get("startDate"), LocalDateTime.now()));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("product").get("name")), pattern),
                    cb.like(cb.lower(root.get("label")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
