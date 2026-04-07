package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.SaleReturn;
import com.proconsi.electrobazar.model.PaymentMethod;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ReturnSpecification {

    public static Specification<SaleReturn> filterReturns(String search, String method, String date) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String lSearch = "%" + search.toLowerCase() + "%";
                Predicate num = cb.like(cb.lower(root.get("returnNumber")), lSearch);
                Predicate reason = cb.like(cb.lower(root.get("reason")), lSearch);
                // Also search in original sale number? (invoice or ticket)
                // This might be complex as it depends on join.
                predicates.add(cb.or(num, reason));
            }

            if (method != null && !method.isBlank()) {
                try {
                    // Try to match partial string (e.g. "Efectivo" vs "CASH")
                    PaymentMethod pm = null;
                    if (method.equalsIgnoreCase("Efectivo")) pm = PaymentMethod.CASH;
                    else if (method.equalsIgnoreCase("Tarjeta")) pm = PaymentMethod.CARD;
                    else pm = PaymentMethod.valueOf(method.toUpperCase());
                    
                    if (pm != null) {
                        predicates.add(cb.equal(root.get("paymentMethod"), pm));
                    }
                } catch (Exception e) {
                    // ignore invalid enum
                }
            }

            if (date != null && !date.isBlank()) {
                LocalDate localDate = LocalDate.parse(date);
                predicates.add(cb.between(root.get("createdAt"), localDate.atStartOfDay(), localDate.atTime(LocalTime.MAX)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
