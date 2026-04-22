package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.InvoiceSequence;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.Ticket;
import com.proconsi.electrobazar.repository.InvoiceSequenceRepository;
import com.proconsi.electrobazar.repository.TicketRepository;
import com.proconsi.electrobazar.service.CompanySettingsService;
import com.proconsi.electrobazar.service.TicketService;
import com.proconsi.electrobazar.service.VerifactuService;
import com.proconsi.electrobazar.util.VerifactuHashCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@Slf4j
public class TicketServiceImpl implements TicketService {

    private static final String TICKET_SERIE = "T";

    private final TicketRepository ticketRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final com.proconsi.electrobazar.service.InvoiceService invoiceService;
    private final VerifactuHashCalculator hashCalculator;
    private final VerifactuService verifactuService;
    private final CompanySettingsService companySettingsService;

    public TicketServiceImpl(
            TicketRepository ticketRepository,
            InvoiceSequenceRepository invoiceSequenceRepository,
            com.proconsi.electrobazar.service.InvoiceService invoiceService,
            VerifactuHashCalculator hashCalculator,
            @Lazy VerifactuService verifactuService,
            CompanySettingsService companySettingsService) {
        this.ticketRepository = ticketRepository;
        this.invoiceSequenceRepository = invoiceSequenceRepository;
        this.invoiceService = invoiceService;
        this.hashCalculator = hashCalculator;
        this.verifactuService = verifactuService;
        this.companySettingsService = companySettingsService;
    }

    @Override
    @Transactional
    public Ticket createTicket(Sale sale, boolean applyRecargo) {
        int ticketYear = (sale.getCreatedAt() != null) ? sale.getCreatedAt().getYear() : LocalDate.now().getYear();
        String serie = TICKET_SERIE;

        InvoiceSequence sequence = invoiceSequenceRepository.findBySerieAndYearForUpdate(serie, ticketYear)
                .orElseGet(() -> invoiceSequenceRepository.save(
                        InvoiceSequence.builder().serie(serie).year(ticketYear).lastNumber(0).build()));

        String ticketNumber;
        do {
            int nextNumber = sequence.getLastNumber() + 1;
            sequence.setLastNumber(nextNumber);
            invoiceSequenceRepository.save(sequence);
            ticketNumber = String.format("%s-%d-%d", serie, ticketYear, nextNumber);
        } while (ticketRepository.findByTicketNumber(ticketNumber).isPresent());

        String previousHash = ticketRepository.findFirstByOrderByCreatedAtDesc()
                .map(Ticket::getHashCurrentInvoice)
                .orElse(VerifactuHashCalculator.INITIAL_HASH);

        // Snapshot the current return deadline so future changes don't affect this ticket.
        Integer deadlineDays = companySettingsService.getSettings().getReturnDeadlineDays();
        if (deadlineDays == null || deadlineDays <= 0) deadlineDays = 15;

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber).serie(serie).year(ticketYear)
                .sequenceNumber(sequence.getLastNumber()).sale(sale).applyRecargo(applyRecargo)
                .hashPreviousInvoice(previousHash)
                .aeatStatus(AeatStatus.PENDING_SEND)
                .returnDeadlineDays(deadlineDays)
                .build();

        if (ticket.getCreatedAt() == null) ticket.prePersist();

        ticket.setHashCurrentInvoice(invoiceService.calculateHash(ticket, previousHash));

        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket creado: {} para Venta #{}", ticketNumber, sale.getId());

        verifactuService.submitTicketAsync(saved.getId());
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
