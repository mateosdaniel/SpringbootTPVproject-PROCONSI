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
import com.proconsi.electrobazar.service.ActivityLogService;
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

/**
 * Implementation of {@link ReturnService}.
 * Handles complex return flows, including stock restoration, cash liquidity verification,
 * and generation of corrective legal documents (FR - Factura Rectificativa).
 */
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
    private final RectificativeInvoiceRepository rectificativeInvoiceRepository;
    private final ActivityLogService activityLogService;

    private static final String INITIAL_HASH = "0000000000000000";

    @Override
    @Transactional
    public SaleReturn processReturn(Long originalSaleId, List<ReturnLineRequest> lineRequests,
            String reason, PaymentMethod paymentMethod, Worker worker) {
        
        // POS Business Rule: Verifies that a cash register session is open for today before processing returns.
        cashRegisterService.checkOpenRegisterForToday();

        Sale originalSale = saleService.findById(originalSaleId);
        List<ReturnLine> returnLines = new ArrayList<>();
        BigDecimal totalRefunded = BigDecimal.ZERO;
        int totalOriginalUnits = 0;
        int totalReturnedUnits = 0;

        for (ReturnLineRequest req : lineRequests) {
            if (req.getQuantity() <= 0) continue;

            SaleLine saleLine = saleLineRepository.findById(req.getSaleLineId())
                    .orElseThrow(() -> new IllegalArgumentException("Sale line " + req.getSaleLineId() + " not found."));

            if (!saleLine.getSale().getId().equals(originalSaleId)) {
                throw new IllegalArgumentException("Cross-sale return violation detected.");
            }

            int alreadyReturned = returnLineRepository.sumReturnedQuantityBySaleLineId(saleLine.getId());
            int availableToReturn = saleLine.getQuantity() - alreadyReturned;

            if (req.getQuantity() > availableToReturn) {
                throw new IllegalArgumentException(String.format("Over-return error: %d units available, %d requested.", 
                        availableToReturn, req.getQuantity()));
            }

            BigDecimal vatRate = (saleLine.getVatRate() != null) ? saleLine.getVatRate() : new BigDecimal("0.21");
            BigDecimal lineSubtotal = saleLine.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())).setScale(2, RoundingMode.HALF_UP);

            ReturnLine returnLine = ReturnLine.builder()
                    .saleLine(saleLine).quantity(req.getQuantity()).unitPrice(saleLine.getUnitPrice())
                    .subtotal(lineSubtotal).vatRate(vatRate).build();

            returnLines.add(returnLine);
            totalRefunded = totalRefunded.add(lineSubtotal);
            totalOriginalUnits += saleLine.getQuantity();
            totalReturnedUnits += (alreadyReturned + req.getQuantity());

            // 1. Inventory Restoration
            productService.increaseStock(saleLine.getProduct().getId(), req.getQuantity());
        }

        if (returnLines.isEmpty()) {
            throw new IllegalArgumentException("No products selected for return.");
        }

        // 2. Liquidity Check for Cash Refunds
        if (paymentMethod == PaymentMethod.CASH) {
            BigDecimal currentDrawerCash = cashRegisterService.getCurrentCashBalance();
            if (totalRefunded.compareTo(currentDrawerCash) > 0) {
                log.warn("Blocked cash refund: -%.2f € requested, %.2f € available in drawer.", totalRefunded, currentDrawerCash);
                throw new InsufficientCashException("Insufficient cash in drawer to process this refund.");
            }
        }

        SaleReturn.ReturnType returnType = (totalOriginalUnits == totalReturnedUnits) ? SaleReturn.ReturnType.TOTAL : SaleReturn.ReturnType.PARTIAL;
        String returnNumber = generateNumber(RETURN_SERIE);

        SaleReturn saleReturn = SaleReturn.builder()
                .returnNumber(returnNumber).originalSale(originalSale).worker(worker)
                .reason(reason).type(returnType).totalRefunded(totalRefunded.setScale(2, RoundingMode.HALF_UP))
                .paymentMethod(paymentMethod).build();

        for (ReturnLine line : returnLines) line.setSaleReturn(saleReturn);
        saleReturn.setLines(returnLines);

        SaleReturn saved = saleReturnRepository.save(saleReturn);

        // 3. Document Invalidation (Audit)
        if (returnType == SaleReturn.ReturnType.TOTAL) {
            invoiceService.findBySaleId(originalSaleId).ifPresent(invoice -> {
                invoice.setStatus(Invoice.InvoiceStatus.RECTIFIED);
                invoiceRepository.save(invoice);
            });
        }

        // 4. Corrective Invoice Generation (Fiscal Requirement with Verifactu Chaining)
        if (originalSale.getInvoice() != null) {
            String rectNumber = generateNumber("FR");
            
            // Verifactu Chaining: Get previous hash for FR series
            String previousHash = rectificativeInvoiceRepository.findFirstByOrderByCreatedAtDesc()
                    .map(RectificativeInvoice::getHashCurrentInvoice)
                    .orElse(INITIAL_HASH);

            RectificativeInvoice rect = RectificativeInvoice.builder()
                    .rectificativeNumber(rectNumber)
                    .saleReturn(saved)
                    .originalInvoice(originalSale.getInvoice())
                    .reason(reason != null && !reason.isBlank() ? reason : "Return of goods")
                    .hashPreviousInvoice(previousHash)
                    .build();

            // Set creation date for hash calculation if necessary
            if (rect.getCreatedAt() == null) {
                rect.prePersist();
            }
            
            rect.setHashCurrentInvoice(invoiceService.calculateHash(rect, previousHash));
            
            rectificativeInvoiceRepository.save(rect);
            saved.setRectificativeInvoice(rect);
        }

        String username = (worker != null) ? worker.getUsername() : "System";
        activityLogService.logActivity("DEVOLUCIÓN", 
                String.format("Devolución %s procesada para Venta nº %d. Total: -%.2f €. Pago: %s", 
                returnNumber, originalSaleId, totalRefunded, paymentMethod.name()), username, "RETURN", saved.getId());

        return saved;
    }

    /**
     * Protected utility to generate correlative numbers for Returns (D-*) or Corrective Invoices (FR-*).
     */
    private String generateNumber(String serie) {
        int currentYear = LocalDate.now().getYear();
        InvoiceSequence sequence = invoiceSequenceRepository.findBySerieAndYearForUpdate(serie, currentYear)
                .orElseGet(() -> invoiceSequenceRepository.save(InvoiceSequence.builder().serie(serie).year(currentYear).lastNumber(0).build()));

        int next = sequence.getLastNumber() + 1;
        sequence.setLastNumber(next);
        invoiceSequenceRepository.save(sequence);

        return (serie.equals("FR")) ? String.format("%s-%d-%04d", serie, currentYear, next) : String.format("%s-%d-%04d", serie, currentYear, next);
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
        LocalDateTime start = cashRegisterService.getOpenRegister()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(LocalDate.now().atStartOfDay());
        return sumTotalRefundedBetweenByPaymentMethod(start, LocalDateTime.now(), method);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalRefundedBetweenByPaymentMethod(LocalDateTime from, LocalDateTime to, PaymentMethod method) {
        return saleReturnRepository.sumTotalRefundedBetweenByPaymentMethod(from, to, method);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleReturn> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to) {
        return saleReturnRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }
}


