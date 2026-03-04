package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /** Finds the ticket associated with a specific sale ID. */
    @Query("SELECT t FROM Ticket t WHERE t.sale.id = :saleId")
    Optional<Ticket> findBySaleId(@Param("saleId") Long saleId);

    /** Find by formatted ticket number (e.g. "T-2026-0001"). */
    Optional<Ticket> findByTicketNumber(String ticketNumber);
}
