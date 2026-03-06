package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.InvoiceSequence;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.InvoiceSequenceRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private static final String DEFAULT_SERIE = "F";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final ActivityLogService activityLogService;

    /**
     * Atomically increments the invoice sequence for the current year and serie,
     * then persists and returns the new Invoice.
     *
     * The PESSIMISTIC_WRITE lock on the InvoiceSequence row guarantees that two
     * concurrent transactions cannot read the same lastNumber before one of them
     * commits the increment, preventing duplicate invoice numbers.
     */
    @Override
    @Transactional
    public Invoice createInvoice(Sale sale) {
        int currentYear = LocalDate.now().getYear();
        String serie = DEFAULT_SERIE;

        // Fetch (and lock) the sequence row for this serie+year, creating it if absent.
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

        // Increment and persist the next available sequence number.
        // We use a loop and checking current repository to ensure strict sequentiality
        // and no gaps.
        String invoiceNumber;
        do {
            int nextNumber = sequence.getLastNumber() + 1;
            sequence.setLastNumber(nextNumber);
            invoiceSequenceRepository.save(sequence);

            // Format: F-2026-0001 (serie, year, zero-padded 4-digit sequence)
            invoiceNumber = String.format("%s-%d-%04d", serie, currentYear, nextNumber);
        } while (invoiceRepository.findByInvoiceNumber(invoiceNumber).isPresent());

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .serie(serie)
                .year(currentYear)
                .sequenceNumber(sequence.getLastNumber())
                .sale(sale)
                .status(Invoice.InvoiceStatus.ACTIVE)
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice created: {} for sale #{}", invoiceNumber, sale.getId());

        activityLogService.logActivity(
                "CREAR_FACTURA",
                "Factura generada: " + invoiceNumber + " (Venta #" + sale.getId() + ")",
                "Sistema",
                "INVOICE",
                saved.getId());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Invoice> findBySaleId(Long saleId) {
        return invoiceRepository.findBySaleId(saleId);
    }
}
