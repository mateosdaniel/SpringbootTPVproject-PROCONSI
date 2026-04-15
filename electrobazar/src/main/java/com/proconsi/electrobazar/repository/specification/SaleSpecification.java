package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SaleSpecification {

    public static Specification<Sale> filterSales(String search, String type, String method, LocalDate date) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Optimización: Solo cargar relaciones si no es un conteo de páginas
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("invoice", JoinType.LEFT);
                root.fetch("ticket", JoinType.LEFT);
            }

            // 2. BÚSQUEDA POR TEXTO (Bloque Único)
            if (search != null && !search.trim().isEmpty()) {
                System.out.println("DEBUG: Buscando el término -> [" + search + "]");
                
                // FORZADO DE ERROR PARA DIAGNÓSTICO: La tabla debe quedar VACÍA si hay búsqueda
                return cb.disjunction();

                /* 
                // Resto del código comentado temporalmente por el usuario para diagnóstico
                String cleanSearch = search.trim().toLowerCase();
                ...
                */
            }

            // 3. FILTROS FIJOS (Si están seleccionados, deben cumplirse SIEMPRE)
            if (type != null && !type.isBlank()) {
                if ("factura".equalsIgnoreCase(type))
                    predicates.add(cb.isNotNull(root.get("invoice")));
                else if ("ticket".equalsIgnoreCase(type))
                    predicates.add(cb.isNotNull(root.get("ticket")));
            }
            if (method != null && !method.isBlank()) {
                PaymentMethod pm = "Efectivo".equalsIgnoreCase(method) ? PaymentMethod.CASH : PaymentMethod.CARD;
                predicates.add(cb.equal(root.get("paymentMethod"), pm));
            }
            if (date != null) {
                predicates.add(cb.between(root.get("createdAt"), date.atStartOfDay(), date.atTime(23, 59, 59)));
            }

            // 4. ORDENACIÓN
            query.orderBy(cb.desc(root.get("createdAt")));

            // Si no hay filtros, devolvemos todo. Si hay, aplicamos el AND de todos ellos.
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}