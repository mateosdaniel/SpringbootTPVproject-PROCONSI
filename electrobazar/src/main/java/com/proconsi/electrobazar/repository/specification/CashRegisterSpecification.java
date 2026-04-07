package com.proconsi.electrobazar.repository.specification;

import com.proconsi.electrobazar.model.CashRegister;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CashRegisterSpecification {

    public static Specification<CashRegister> filterRegisters(String worker, String date) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (worker != null && !worker.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("worker").get("username")), "%" + worker.toLowerCase() + "%"));
            }

            if (date != null && !date.isBlank()) {
                LocalDate localDate = LocalDate.parse(date);
                predicates.add(cb.between(root.get("openingTime"), localDate.atStartOfDay(), localDate.atTime(LocalTime.MAX)));
            }

            // Only show closed ones for this view typically? 
            // Or all. Usually history is closed ones.
            predicates.add(cb.isNotNull(root.get("closedAt")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
