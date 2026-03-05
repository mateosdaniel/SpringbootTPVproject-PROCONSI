package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.Ticket;

import java.util.Optional;

public interface TicketService {

    /**
     * Atomically creates a new ticket for the given sale, assigning the next
     * correlative number in the current year's ticket series ("T").
     * Uses pessimistic locking on the invoice_sequences table.
     *
     * @param sale         The sale to generate a ticket for
     * @param applyRecargo Whether recargo was applied during sale
     * @return The newly created Ticket
     */
    Ticket createTicket(Sale sale, boolean applyRecargo);

    /**
     * Finds the ticket associated with a specific sale ID.
     *
     * @param saleId The sale ID
     * @return Optional containing the Ticket or empty
     */
    Optional<Ticket> findBySaleId(Long saleId);

    /**
     * Finds a ticket by its formatted ticket number (e.g. "T-2026-0001").
     *
     * @param ticketNumber The ticket number
     * @return Optional containing the Ticket or empty
     */
    Optional<Ticket> findByTicketNumber(String ticketNumber);
}
