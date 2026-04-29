package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("SELECT t FROM Ticket t WHERE t.sale.id = :saleId")
    Optional<Ticket> findBySaleId(@Param("saleId") Long saleId);

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    Optional<Ticket> findFirstByOrderByCreatedAtDesc();

    Optional<Ticket> findByHashCurrentInvoice(String hashCurrentInvoice);
    
    @EntityGraph(attributePaths = { "sale" })
    @Query("SELECT t FROM Ticket t WHERE t.aeatStatus = :pending " +
           "OR (t.aeatStatus = :rejected AND t.aeatRejectionReason = com.proconsi.electrobazar.model.AeatRejectionReason.NETWORK_ERROR)")
    List<Ticket> findPendingAndRejected(@Param("pending") AeatStatus pending,
                                         @Param("rejected") AeatStatus rejected);

    default List<Ticket> findPendingSend() {
        return findPendingAndRejected(AeatStatus.PENDING_SEND, AeatStatus.REJECTED);
    }
}
