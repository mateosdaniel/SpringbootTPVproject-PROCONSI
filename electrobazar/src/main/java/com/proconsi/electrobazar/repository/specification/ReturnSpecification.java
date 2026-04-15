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

                // Subquery for original sale invoice number — avoids JOIN+distinct pagination bug
                jakarta.persistence.criteria.Subquery<Long> invSub = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<com.proconsi.electrobazar.model.Invoice> invRoot =
                        invSub.from(com.proconsi.electrobazar.model.Invoice.class);
                invSub.select(invRoot.get("id"))
                      .where(cb.and(
                          cb.equal(invRoot.get("sale"), root.get("originalSale")),
                          cb.like(cb.lower(invRoot.get("invoiceNumber")), lSearch)
                      ));

                // Subquery for original sale ticket number
                jakarta.persistence.criteria.Subquery<Long> tickSub = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<com.proconsi.electrobazar.model.Ticket> tickRoot =
                        tickSub.from(com.proconsi.electrobazar.model.Ticket.class);
                tickSub.select(tickRoot.get("id"))
                       .where(cb.and(
                           cb.equal(tickRoot.get("sale"), root.get("originalSale")),
                           cb.like(cb.lower(tickRoot.get("ticketNumber")), lSearch)
                       ));

                predicates.add(cb.or(num, reason, cb.exists(invSub), cb.exists(tickSub)));
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
