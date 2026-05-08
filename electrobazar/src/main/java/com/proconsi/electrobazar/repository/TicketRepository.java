package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @EntityGraph(attributePaths = { "sale", "sale.customer" })
    @Query("SELECT t FROM Ticket t WHERE " +
           "(:status IS NULL OR t.aeatStatus = :status) AND " +
           "(:reason IS NULL OR t.aeatRejectionReason = :reason) AND " +
           "(:start IS NULL OR t.createdAt >= :start) AND " +
           "(:end IS NULL OR t.createdAt <= :end)")
    Page<Ticket> findByFilters(
            @Param("status") AeatStatus status,
            @Param("reason") com.proconsi.electrobazar.model.AeatRejectionReason reason,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @EntityGraph(attributePaths = { "sale", "sale.customer" })
    @Query("SELECT t FROM Ticket t")
    Page<Ticket> findAllPaginated(Pageable pageable);

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
