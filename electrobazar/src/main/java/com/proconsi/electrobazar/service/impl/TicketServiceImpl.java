package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.InvoiceSequence;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.Ticket;
import com.proconsi.electrobazar.repository.InvoiceSequenceRepository;
import com.proconsi.electrobazar.repository.TicketRepository;
import com.proconsi.electrobazar.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Implementation of {@link TicketService}.
 * Responsible for generating simplified sales receipts (Tickets) with sequential numbering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private static final String TICKET_SERIE = "T";

    private final TicketRepository ticketRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final com.proconsi.electrobazar.service.InvoiceService invoiceService;

    private static final String INITIAL_HASH = "0000000000000000";

    @Override
    @Transactional
    public Ticket createTicket(Sale sale, boolean applyRecargo) {
        int ticketYear = (sale.getCreatedAt() != null) ? sale.getCreatedAt().getYear() : LocalDate.now().getYear();
        String serie = TICKET_SERIE;

        // Fetch (and lock) the sequence row for serie "T"
        InvoiceSequence sequence = invoiceSequenceRepository.findBySerieAndYearForUpdate(serie, ticketYear)
                .orElseGet(() -> invoiceSequenceRepository.save(
                        InvoiceSequence.builder().serie(serie).year(ticketYear).lastNumber(0).build()
                ));

        String ticketNumber;
        do {
            int nextNumber = sequence.getLastNumber() + 1;
            sequence.setLastNumber(nextNumber);
            invoiceSequenceRepository.save(sequence);

            // Format example: T-2026-1
            ticketNumber = String.format("%s-%d-%d", serie, ticketYear, nextNumber);
        } while (ticketRepository.findByTicketNumber(ticketNumber).isPresent());

        // Verifactu Chaining: Get previous hash for Ticket series
        String previousHash = ticketRepository.findFirstByOrderByCreatedAtDesc()
                .map(Ticket::getHashCurrentInvoice)
                .orElse(INITIAL_HASH);

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber).serie(serie).year(ticketYear)
                .sequenceNumber(sequence.getLastNumber()).sale(sale).applyRecargo(applyRecargo)
                .hashPreviousInvoice(previousHash)
                .build();

        // Set creation date for hash calculation if necessary
        if (ticket.getCreatedAt() == null) {
            ticket.prePersist();
        }

        ticket.setHashCurrentInvoice(invoiceService.calculateHash(ticket, previousHash));

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket created: {} for Sale #{}", ticketNumber, sale.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> findBySaleId(Long saleId) {
        return ticketRepository.findBySaleId(saleId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> findByTicketNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber);
    }
}


