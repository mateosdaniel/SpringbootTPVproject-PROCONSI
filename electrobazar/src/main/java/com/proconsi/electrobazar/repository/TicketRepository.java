package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Ticket} entities.
 * Manages simplified receipts (Tickets) generated for TPV sales.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Finds the simplified receipt associated with a specific sale ID.
     */
    @Query("SELECT t FROM Ticket t WHERE t.sale.id = :saleId")
    Optional<Ticket> findBySaleId(@Param("saleId") Long saleId);

    /**
     * Finds a ticket by its unique formatted number (e.g., "T-2026-0001").
     */
    Optional<Ticket> findByTicketNumber(String ticketNumber);
}


