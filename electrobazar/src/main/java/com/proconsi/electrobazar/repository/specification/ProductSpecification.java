package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.Product;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> filterProducts(String search, String category, String stockFilter,
            Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Evitar N+1 queries forzando un JOIN FETCH con categoria
            // solo en queries transaccionales, no en cuentas
            if (Long.class != query.getResultType()) {
                root.fetch("category", jakarta.persistence.criteria.JoinType.LEFT);
            }

            // 1. Buscador global (Por nombre, descripción o ID exacto)
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.toLowerCase().trim() + "%";
                Predicate namePredicate = cb.like(cb.lower(root.get("name")), searchPattern);
                Predicate descPredicate = cb.like(cb.lower(root.get("description")), searchPattern);

                try {
                    Long idSearch = Long.parseLong(search.trim());
                    Predicate idPredicate = cb.equal(root.get("id"), idSearch);
                    predicates.add(cb.or(namePredicate, descPredicate, idPredicate));
                } catch (NumberFormatException e) {
                    predicates.add(cb.or(namePredicate, descPredicate));
                }
            }

            // 2. Filtro por Categoría (Nombre tal como lo usa el frontend)
            if (category != null && !category.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("category").get("name"), category));
            }

            // 3. Filtro de Stock
            if (stockFilter != null && !stockFilter.trim().isEmpty()) {
                if ("low".equalsIgnoreCase(stockFilter)) {
                    predicates.add(cb.lessThan(root.get("stock"), 5));
                } else if ("normal".equalsIgnoreCase(stockFilter)) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("stock"), 5));
                }
            }

            // 4. Estado "Activo"
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            // Ordenamiento por defecto
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
