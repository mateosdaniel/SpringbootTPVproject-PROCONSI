package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of {@link SaleService}.
 * Central service for processing TPV sales, managing tax breakdowns, stock deductions,
 * and linking sales with customers, workers, and tariffs.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final int SCALE = 2;

    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final CashRegisterRepository cashRegisterRepository;
    private final ActivityLogService activityLogService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final TariffRepository tariffRepository;

    @Override
    @Transactional(readOnly = true)
    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findAll() {
        return saleRepository.findAllWithDetails();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findToday() {
        return saleRepository.findToday();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sale> findBetween(LocalDateTime from, LocalDateTime to) {
        return saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);
    }

    @Override
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, Worker worker) {
        return createSale(lines, paymentMethod, notes, receivedAmount, null, worker);
    }

    @Override
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, Customer customer, Worker worker) {
        return createSaleWithTariff(lines, paymentMethod, notes, receivedAmount, customer, worker, null);
    }

    @Override
    public Sale createSaleWithTariff(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, Customer customer, Worker worker, Tariff tariffOverride) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A sale must contain at least one line.");
        }

        // 1. Resolve effective tariff
        Tariff effective = (tariffOverride != null) ? tariffOverride : 
                          ((customer != null && customer.getTariff() != null) ? customer.getTariff() : 
                          tariffRepository.findByName(Tariff.MINORISTA).orElse(null));

        String tariffName = (effective != null) ? effective.getName() : Tariff.MINORISTA;
        BigDecimal discountPct = (effective != null && effective.getDiscountPercentage() != null) ? effective.getDiscountPercentage() : BigDecimal.ZERO;

        // 2. Process stock and build tax breakdown
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal totalRecargo = BigDecimal.ZERO;
        boolean applyRE = (customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia()));

        for (SaleLine line : lines) {
            productService.decreaseStock(line.getProduct().getId(), line.getQuantity());

            BigDecimal finalGross = line.getUnitPrice().setScale(SCALE, ROUNDING);
            line.setOriginalUnitPrice(finalGross);
            line.setDiscountPercentage(BigDecimal.ZERO); // Applied beforehand by TPV logic
            line.setUnitPrice(finalGross);

            BigDecimal vatRate = (line.getVatRate() != null) ? line.getVatRate() : 
                                 (line.getProduct().getTaxRate() != null ? line.getProduct().getTaxRate().getVatRate() : new BigDecimal("0.21"));

            TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(line.getProduct().getId(), line.getProduct().getName(), finalGross, line.getQuantity(), vatRate, applyRE);

            line.setBasePriceNet(breakdown.getUnitPrice());
            line.setBaseAmount(breakdown.getBaseAmount());
            line.setVatAmount(breakdown.getVatAmount());
            line.setRecargoRate(breakdown.getRecargoRate());
            line.setRecargoAmount(breakdown.getRecargoAmount());
            
            BigDecimal lineTotal = finalGross.multiply(BigDecimal.valueOf(line.getQuantity())).setScale(SCALE, ROUNDING).add(line.getRecargoAmount());
            line.setSubtotal(lineTotal);

            total = total.add(lineTotal);
            totalBase = totalBase.add(line.getBaseAmount());
            totalVat = totalVat.add(line.getVatAmount());
            totalRecargo = totalRecargo.add(line.getRecargoAmount());
        }

        BigDecimal finalTotal = total.setScale(SCALE, ROUNDING);
        BigDecimal change = null;
        if (paymentMethod == PaymentMethod.CASH && receivedAmount != null) {
            // Tolerance to handle tiny rounding deviations between JS client and Server
            if (receivedAmount.add(new BigDecimal("0.01")).compareTo(finalTotal) < 0) {
                throw new IllegalArgumentException("Received amount is less than total sale amount.");
            }
            change = receivedAmount.subtract(finalTotal);
        }

        Sale sale = Sale.builder()
                .paymentMethod(paymentMethod).totalAmount(finalTotal).totalBase(totalBase.setScale(SCALE, ROUNDING))
                .totalVat(totalVat.setScale(SCALE, ROUNDING)).totalRecargo(totalRecargo.setScale(SCALE, ROUNDING))
                .totalDiscount(BigDecimal.ZERO).applyRecargo(applyRE).receivedAmount(receivedAmount).changeAmount(change)
                .cashAmount(paymentMethod == PaymentMethod.CASH ? finalTotal : BigDecimal.ZERO)
                .cardAmount(paymentMethod == PaymentMethod.CARD ? finalTotal : BigDecimal.ZERO)
                .notes(notes).customer(customer).worker(worker).lines(lines).appliedTariff(tariffName)
                .appliedDiscountPercentage(discountPct.setScale(SCALE, ROUNDING)).build();

        lines.forEach(l -> l.setSale(sale));
        Sale saved = saleRepository.save(sale);

        String username = (worker != null) ? worker.getUsername() : "Anonymous";
        activityLogService.logActivity("VENTA", String.format("Sale processed by %s. Total: %.2f € (Tariff: %s)", username, finalTotal, tariffName), username, "SALE", saved.getId());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalToday() {
        LocalDateTime start = getShiftStart();
        return saleRepository.sumTotalBetween(start, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public long countToday() {
        return saleRepository.countByCreatedAtBetween(getShiftStart(), LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalByPaymentMethodToday(PaymentMethod method) {
        return saleRepository.sumTotalBetweenByPaymentMethod(getShiftStart(), LocalDateTime.now(), method).orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public com.proconsi.electrobazar.dto.SaleSummaryResponse getSummaryToday() {
        return saleRepository.getSummaryBetween(getShiftStart(), LocalDateTime.now());
    }

    private LocalDateTime getShiftStart() {
        return cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(LocalDate.now().atStartOfDay());
    }

    @Override
    @Transactional
    public void saveApplyRecargo(Long saleId, boolean apply) {
        saleRepository.findById(saleId).ifPresent(s -> {
            if (s.isApplyRecargo() != apply) {
                s.setApplyRecargo(apply);
                saleRepository.save(s);
                activityLogService.logActivity("MODIFICAR_RECARGO", 
                        "RE status changed on Sale #" + saleId + (apply ? " (Enabled)" : " (Disabled)"), "Admin", "SALE", saleId);
            }
        });
    }

    @Override
    @Transactional
    public void cancelSale(Long id, Worker worker, String reason) {
        Sale sale = findById(id);
        if (sale.getStatus() == Sale.SaleStatus.CANCELLED) throw new IllegalStateException("Sale already cancelled.");

        // Inventory Restoration
        sale.getLines().forEach(l -> productService.increaseStock(l.getProduct().getId(), l.getQuantity()));

        sale.setStatus(Sale.SaleStatus.CANCELLED);
        sale.setNotes((sale.getNotes() != null ? sale.getNotes() + " | " : "") + "ANNULLED: " + reason);
        saleRepository.save(sale);

        String username = (worker != null) ? worker.getUsername() : "System";
        activityLogService.logActivity("ANULAR_VENTA", String.format("Sale #%d annulled by %s. Reason: %s", id, username, reason), username, "SALE", id);
    }
}