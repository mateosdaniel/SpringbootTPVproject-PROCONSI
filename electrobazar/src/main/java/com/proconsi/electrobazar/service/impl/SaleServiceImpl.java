package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.CouponRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CashRegisterService;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final InvoiceService invoiceService;
    private final CouponRepository couponRepository;
    private final CashRegisterService cashRegisterService;

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
    public Page<Sale> findAll(Pageable pageable) {
        return saleRepository.findAll(pageable);
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
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Worker worker) {
        return createSale(lines, paymentMethod, notes, receivedAmount, cashAmount, cardAmount, null, worker);
    }

    @Override
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer, Worker worker) {
        return createSaleWithTariff(lines, paymentMethod, notes, receivedAmount, cashAmount, cardAmount, customer, worker, null);
    }

    @Override
    public Sale createSaleWithCoupon(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer,
            Worker worker, Tariff tariffOverride, String couponCode) {
        
        // 1. Resolve and Validate Coupon
        Coupon coupon = null;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            coupon = couponRepository.findByCodeIgnoreCase(couponCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Cupón no válido o inexistente: " + couponCode));
            
            if (!coupon.isValid()) {
                throw new IllegalStateException("El cupón ha expirado o ha alcanzado su límite de uso.");
            }
        }

        // 2. Initial logic
        cashRegisterService.checkOpenRegisterForToday();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A sale must contain at least one line.");
        }

        Tariff effective = (tariffOverride != null) ? tariffOverride : 
                          ((customer != null && customer.getTariff() != null) ? customer.getTariff() : 
                          tariffRepository.findByName(Tariff.MINORISTA).orElse(null));

        String tariffName = (effective != null) ? effective.getName() : Tariff.MINORISTA;
        BigDecimal tariffDiscountPct = (effective != null && effective.getDiscountPercentage() != null) ? effective.getDiscountPercentage() : BigDecimal.ZERO;

        // 3. Calculation Loop
        BigDecimal subtotalBeforeCoupon = BigDecimal.ZERO;
        boolean applyRE = (customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia()));

        for (SaleLine line : lines) {
            BigDecimal finalGross = line.getUnitPrice().setScale(SCALE, ROUNDING);
            subtotalBeforeCoupon = subtotalBeforeCoupon.add(finalGross.multiply(BigDecimal.valueOf(line.getQuantity())));
        }

        // Calculate actual coupon discount amount
        if (coupon != null) {
            if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
                couponDiscount = subtotalBeforeCoupon.multiply(coupon.getDiscountValue().divide(new BigDecimal("100"), 10, ROUNDING));
            } else {
                couponDiscount = coupon.getDiscountValue();
            }
            if (couponDiscount.compareTo(subtotalBeforeCoupon) > 0) {
                couponDiscount = subtotalBeforeCoupon;
            }
        }

        // 4. Distribute coupon discount proportionally
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal totalRecargo = BigDecimal.ZERO;

        for (int i = 0; i < lines.size(); i++) {
            SaleLine line = lines.get(i);
            productService.decreaseStock(line.getProduct().getId(), line.getQuantity());

            BigDecimal lineGrossBeforeCoupon = line.getUnitPrice();
            BigDecimal lineTotalBeforeCoupon = lineGrossBeforeCoupon.multiply(BigDecimal.valueOf(line.getQuantity()));
            
            BigDecimal lineCouponDiscount = BigDecimal.ZERO;
            if (subtotalBeforeCoupon.compareTo(BigDecimal.ZERO) > 0) {
                lineCouponDiscount = couponDiscount.multiply(lineTotalBeforeCoupon)
                        .divide(subtotalBeforeCoupon, 10, ROUNDING);
            }
            
            BigDecimal finalLineTotal = lineTotalBeforeCoupon.subtract(lineCouponDiscount);
            if (finalLineTotal.compareTo(BigDecimal.ZERO) < 0) finalLineTotal = BigDecimal.ZERO;
            
            BigDecimal effectiveUnitPrice = finalLineTotal.divide(BigDecimal.valueOf(line.getQuantity()), 10, ROUNDING);
            
            BigDecimal vatRate = (line.getVatRate() != null) ? line.getVatRate() : 
                                 (line.getProduct().getTaxRate() != null ? line.getProduct().getTaxRate().getVatRate() : new BigDecimal("0.21"));

            TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(line.getProduct().getId(), line.getProduct().getName(), effectiveUnitPrice, line.getQuantity(), vatRate, applyRE);

            line.setOriginalUnitPrice(lineGrossBeforeCoupon.setScale(SCALE, ROUNDING));
            line.setUnitPrice(effectiveUnitPrice.setScale(SCALE, ROUNDING));
            line.setBasePriceNet(breakdown.getUnitPrice());
            line.setBaseAmount(breakdown.getBaseAmount());
            line.setVatAmount(breakdown.getVatAmount());
            line.setRecargoRate(breakdown.getRecargoRate());
            line.setRecargoAmount(breakdown.getRecargoAmount());
            line.setSubtotal(finalLineTotal.add(line.getRecargoAmount()).setScale(SCALE, ROUNDING));

            total = total.add(line.getSubtotal());
            totalBase = totalBase.add(line.getBaseAmount());
            totalVat = totalVat.add(line.getVatAmount());
            totalRecargo = totalRecargo.add(line.getRecargoAmount());
        }

        BigDecimal finalTotal = total.setScale(SCALE, ROUNDING);

        // 5. Fiscal
        if (paymentMethod == PaymentMethod.CASH) {
            BigDecimal limit = (customer != null && customer.getType() == Customer.CustomerType.COMPANY) ? new BigDecimal("1000") : new BigDecimal("10000");
            if (finalTotal.compareTo(limit) > 0) {
                 if (customer != null && customer.getType() == Customer.CustomerType.COMPANY) throw new IllegalStateException("Excede el límite de efectivo real decreto.");
            }
        }

        BigDecimal change = null;
        BigDecimal actualCashAmt = BigDecimal.ZERO;
        BigDecimal actualCardAmt = BigDecimal.ZERO;

        if (paymentMethod == PaymentMethod.CASH) {
            BigDecimal rAmt = receivedAmount != null ? receivedAmount : finalTotal;
            change = rAmt.subtract(finalTotal);
            actualCashAmt = finalTotal;
        } else if (paymentMethod == PaymentMethod.CARD) {
            actualCardAmt = finalTotal;
        } else if (paymentMethod == PaymentMethod.MIXED) {
            BigDecimal totalReceived = (cardAmount != null ? cardAmount : BigDecimal.ZERO).add(cashAmount != null ? cashAmount : BigDecimal.ZERO);
            change = totalReceived.subtract(finalTotal);
            actualCardAmt = cardAmount != null ? cardAmount : BigDecimal.ZERO;
            actualCashAmt = (cashAmount != null ? cashAmount : BigDecimal.ZERO).subtract(change);
        }

        Sale sale = Sale.builder()
                .paymentMethod(paymentMethod).totalAmount(finalTotal).totalBase(totalBase.setScale(SCALE, ROUNDING))
                .totalVat(totalVat.setScale(SCALE, ROUNDING)).totalRecargo(totalRecargo.setScale(SCALE, ROUNDING))
                .totalDiscount(couponDiscount.setScale(SCALE, ROUNDING)).applyRecargo(applyRE).receivedAmount(receivedAmount).changeAmount(change)
                .cashAmount(actualCashAmt).cardAmount(actualCardAmt)
                .notes(notes).customer(customer).worker(worker).lines(lines).appliedTariff(tariffName)
                .coupon(coupon)
                .appliedDiscountPercentage(tariffDiscountPct.setScale(SCALE, ROUNDING)).build();

        lines.forEach(l -> l.setSale(sale));
        
        if (coupon != null) {
            coupon.setTimesUsed(coupon.getTimesUsed() + 1);
            couponRepository.save(coupon);
        }

        Sale saved = saleRepository.save(sale);
        String username = (worker != null) ? worker.getUsername() : "Anonymous";
        activityLogService.logActivity("VENTA", String.format("Sale processed by %s. Total: %.2f € (Coupon: %s)", username, finalTotal, (coupon != null ? coupon.getCode() : "None")), username, "SALE", saved.getId());

        return saved;
    }

    @Override
    public Sale createSaleWithTariff(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer, Worker worker, Tariff tariffOverride) {
        return createSaleWithCoupon(lines, paymentMethod, notes, receivedAmount, cashAmount, cardAmount, customer, worker, tariffOverride, null);
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
    @Transactional(readOnly = true)
    public List<com.proconsi.electrobazar.dto.WorkerSaleStatsDTO> getWorkerStatsBetween(LocalDateTime from, LocalDateTime to) {
        return saleRepository.getWorkerStatsBetween(from, to);
    }

    @Override
    @Transactional
    public void cancelSale(Long id, Worker worker, String reason) {
        Sale sale = findById(id);
        if (sale.getStatus() == Sale.SaleStatus.CANCELLED) throw new IllegalStateException("Sale already cancelled.");

        // Legal requirement: Generate rectificative invoice before cancelling if original was invoiced
        if (sale.getInvoice() != null) {
            invoiceService.generateRectificativeInvoice(sale, reason);
        }

        // Inventory Restoration
        sale.getLines().forEach(l -> productService.increaseStock(l.getProduct().getId(), l.getQuantity()));

        sale.setStatus(Sale.SaleStatus.CANCELLED);
        sale.setNotes((sale.getNotes() != null ? sale.getNotes() + " | " : "") + "ANNULLED: " + reason);
        saleRepository.save(sale);

        String username = (worker != null) ? worker.getUsername() : "System";
        activityLogService.logActivity("ANULAR_VENTA", String.format("Sale #%d annulled by %s. Reason: %s", id, username, reason), username, "SALE", id);
    }
}