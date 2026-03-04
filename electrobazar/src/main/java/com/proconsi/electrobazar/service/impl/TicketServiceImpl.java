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

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private static final String TICKET_SERIE = "T";

    private final TicketRepository ticketRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;

    @Override
    @Transactional
    public Ticket createTicket(Sale sale, boolean applyRecargo) {
        int currentYear = LocalDate.now().getYear();
        String serie = TICKET_SERIE;

        // Fetch (and lock) the sequence row for serie "T"
        InvoiceSequence sequence = invoiceSequenceRepository
                .findBySerieAndYearForUpdate(serie, currentYear)
                .orElseGet(() -> {
                    InvoiceSequence newSeq = InvoiceSequence.builder()
                            .serie(serie)
                            .year(currentYear)
                            .lastNumber(0)
                            .build();
                    return invoiceSequenceRepository.save(newSeq);
                });

        int nextNumber = sequence.getLastNumber() + 1;
        sequence.setLastNumber(nextNumber);
        invoiceSequenceRepository.save(sequence);

        // Format: T-2026-0001
        String ticketNumber = String.format("%s-%d-%04d", serie, currentYear, nextNumber);

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber)
                .serie(serie)
                .year(currentYear)
                .sequenceNumber(nextNumber)
                .sale(sale)
                .applyRecargo(applyRecargo)
                .build();

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket created: {} for sale #{}", ticketNumber, sale.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> findBySaleId(Long saleId) {
        return ticketRepository.findBySaleId(saleId);
    }
}
