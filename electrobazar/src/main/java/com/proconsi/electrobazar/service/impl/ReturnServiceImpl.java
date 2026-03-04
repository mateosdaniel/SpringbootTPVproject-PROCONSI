package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.ReturnLineRequest;
import com.proconsi.electrobazar.exception.InsufficientCashException;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.*;
import com.proconsi.electrobazar.service.CashRegisterService;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.ReturnService;
import com.proconsi.electrobazar.service.SaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnServiceImpl implements ReturnService {

    private static final String RETURN_SERIE = "D";

    private final SaleReturnRepository saleReturnRepository;
    private final ReturnLineRepository returnLineRepository;
    private final SaleLineRepository saleLineRepository;
    private final SaleService saleService;
    private final ProductService productService;
    private final InvoiceService invoiceService;
    private final InvoiceSequenceRepository invoiceSequenceRepository;
    private final InvoiceRepository invoiceRepository;
    private final CashRegisterService cashRegisterService;

    @Override
    @Transactional
    public SaleReturn processReturn(Long originalSaleId, List<ReturnLineRequest> lineRequests,
            String reason, PaymentMethod paymentMethod, Worker worker) {

        Sale originalSale = saleService.findById(originalSaleId);
        List<ReturnLine> returnLines = new ArrayList<>();
        BigDecimal totalRefunded = BigDecimal.ZERO;
        int totalOriginalUnits = 0;
        int totalReturnedUnits = 0;

        for (ReturnLineRequest req : lineRequests) {
            if (req.getQuantity() <= 0) {
                continue; // Skip zero-quantity lines (form may submit unchecked lines)
            }

            SaleLine saleLine = saleLineRepository.findById(req.getSaleLineId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "SaleLine not found: " + req.getSaleLineId()));

            // Validate the requested line belongs to the original sale
            if (!saleLine.getSale().getId().equals(originalSaleId)) {
                throw new IllegalArgumentException(
                        "SaleLine " + req.getSaleLineId() + " does not belong to sale " + originalSaleId);
            }

            // Calculate already-returned units for this line across all previous returns
            int alreadyReturned = returnLineRepository.sumReturnedQuantityBySaleLineId(saleLine.getId());
            int availableToReturn = saleLine.getQuantity() - alreadyReturned;

            if (req.getQuantity() > availableToReturn) {
                throw new IllegalArgumentException(
                        "Cannot return " + req.getQuantity() + " units of '" + saleLine.getProduct().getName()
                                + "': only " + availableToReturn + " unit(s) available to return.");
            }

            // Copy vatRate from original line for historical accuracy
            BigDecimal vatRate = saleLine.getVatRate() != null
                    ? saleLine.getVatRate()
                    : new BigDecimal("0.21");

            BigDecimal lineSubtotal = saleLine.getUnitPrice()
                    .multiply(BigDecimal.valueOf(req.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            ReturnLine returnLine = ReturnLine.builder()
                    .saleLine(saleLine)
                    .quantity(req.getQuantity())
                    .unitPrice(saleLine.getUnitPrice())
                    .subtotal(lineSubtotal)
                    .vatRate(vatRate)
                    .build();

            returnLines.add(returnLine);
            totalRefunded = totalRefunded.add(lineSubtotal);
            totalOriginalUnits += saleLine.getQuantity();
            totalReturnedUnits += (alreadyReturned + req.getQuantity());

            // Restore inventory
            productService.increaseStock(saleLine.getProduct().getId(), req.getQuantity());
            log.info("Stock restored: +{} units for product #{} ({})",
                    req.getQuantity(), saleLine.getProduct().getId(), saleLine.getProduct().getName());
        }

        if (returnLines.isEmpty()) {
            throw new IllegalArgumentException("No valid lines to return. Please select at least one product.");
        }

        // Core Requirement: Before processing any return marked as 'CASH', the system
        // must verify if the current CashRegister session has enough physical cash to
        // cover the refund amount.
        if (paymentMethod == PaymentMethod.CASH) {
            BigDecimal currentCashBalance = cashRegisterService.getCurrentCashBalance();
            if (totalRefunded.compareTo(currentCashBalance) > 0) {
                log.warn("Blocked cash return attempt due to insufficient funds: Refund {}, Balance {}",
                        totalRefunded, currentCashBalance);
                throw new InsufficientCashException("Cannot process cash return: Insufficient funds in drawer");
            }
        }

        // Determine return type: TOTAL if all original units from this sale are now
        // returned
        SaleReturn.ReturnType returnType = (totalOriginalUnits == totalReturnedUnits)
                ? SaleReturn.ReturnType.TOTAL
                : SaleReturn.ReturnType.PARTIAL;

        // Assign correlative return number using the same InvoiceSequence table with
        // serie "D"
        String returnNumber = generateReturnNumber();

        SaleReturn saleReturn = SaleReturn.builder()
                .returnNumber(returnNumber)
                .originalSale(originalSale)
                .worker(worker)
                .reason(reason)
                .type(returnType)
                .totalRefunded(totalRefunded.setScale(2, RoundingMode.HALF_UP))
                .paymentMethod(paymentMethod)
                .build();

        // Link lines to the return
        for (ReturnLine line : returnLines) {
            line.setSaleReturn(saleReturn);
        }
        saleReturn.setLines(returnLines);

        SaleReturn saved = saleReturnRepository.save(saleReturn);

        // Mark original invoice as RECTIFIED for total returns
        if (returnType == SaleReturn.ReturnType.TOTAL) {
            invoiceService.findBySaleId(originalSaleId).ifPresent(invoice -> {
                invoice.setStatus(Invoice.InvoiceStatus.RECTIFIED);
                invoiceRepository.save(invoice);
                log.info("Invoice {} marked as RECTIFIED due to total return.", invoice.getInvoiceNumber());
            });
        }

        log.info("Return {} processed for sale #{}: {} refunded ({})",
                returnNumber, originalSaleId, totalRefunded, returnType);
        return saved;
    }

    /**
     * Generates the next correlative return number in format D-YYYY-NNNN,
     * reusing the same InvoiceSequence table with serie "D" and pessimistic
     * locking.
     */
    private String generateReturnNumber() {
        int currentYear = LocalDate.now().getYear();

        InvoiceSequence sequence = invoiceSequenceRepository
                .findBySerieAndYearForUpdate(RETURN_SERIE, currentYear)
                .orElseGet(() -> {
                    InvoiceSequence newSeq = InvoiceSequence.builder()
                            .serie(RETURN_SERIE)
                            .year(currentYear)
                            .lastNumber(0)
                            .build();
                    return invoiceSequenceRepository.save(newSeq);
                });

        int nextNumber = sequence.getLastNumber() + 1;
        sequence.setLastNumber(nextNumber);
        invoiceSequenceRepository.save(sequence);

        return String.format("%s-%d-%04d", RETURN_SERIE, currentYear, nextNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SaleReturn> findById(Long id) {
        return saleReturnRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleReturn> findByOriginalSaleId(Long saleId) {
        return saleReturnRepository.findByOriginalSaleId(saleId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalRefundedTodayByPaymentMethod(PaymentMethod method) {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = cashRegisterService.getOpenRegister()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(today.atStartOfDay());

        LocalDateTime endOfDay = today.atStartOfDay().plusDays(1).minusNanos(1);
        return saleReturnRepository.sumTotalRefundedBetweenByPaymentMethod(startTime, endOfDay, method);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalRefundedBetweenByPaymentMethod(java.time.LocalDateTime from, java.time.LocalDateTime to,
            PaymentMethod method) {
        return saleReturnRepository.sumTotalRefundedBetweenByPaymentMethod(from, to, method);
    }
}
