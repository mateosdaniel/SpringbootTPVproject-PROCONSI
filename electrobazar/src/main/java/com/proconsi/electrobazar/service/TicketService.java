package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.Ticket;
import java.util.Optional;

/**
 * Interface for managing simplified invoices (tickets).
 */
public interface TicketService {

    /**
     * Atomically generates a new ticket for a specific sale.
     * Generates a correlative number based on the current year.
     *
     * @param sale         The sale to associate with the ticket.
     * @param applyRecargo Flag indicating if RE tax was part of the sale.
     * @return The newly created Ticket.
     */
    Ticket createTicket(Sale sale, boolean applyRecargo);

    /**
     * Finds the ticket linked to a specific sale.
     * @param saleId The ID of the sale.
     * @return An Optional containing the Ticket.
     */
    Optional<Ticket> findBySaleId(Long saleId);

    /**
     * Retrieves a ticket using its unique formatted number.
     * @param ticketNumber The number (e.g., "T-2026-0001").
     * @return An Optional containing the Ticket.
     */
    Optional<Ticket> findByTicketNumber(String ticketNumber);
}
