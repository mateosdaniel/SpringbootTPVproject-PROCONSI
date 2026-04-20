package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.Ticket;
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

    @Query("SELECT t FROM Ticket t WHERE t.aeatStatus = :status AND t.aeatRetryCount < :maxRetries")
    List<Ticket> findPendingSend(@Param("maxRetries") int maxRetries,
                                 @Param("status") AeatStatus status);

    default List<Ticket> findPendingSend(int maxRetries) {
        return findPendingSend(maxRetries, AeatStatus.PENDING_SEND);
    }
}
