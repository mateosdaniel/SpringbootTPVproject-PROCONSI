package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.repository.TariffRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private final com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator recargoCalculator;
    private final TariffRepository tariffRepository;

    @Override
    @Transactional(readOnly = true)
    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con id: " + id));
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
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
            com.proconsi.electrobazar.model.Worker worker) {
        return createSale(lines, paymentMethod, notes, receivedAmount, null, worker);
    }

    @Override
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
            com.proconsi.electrobazar.model.Customer customer, com.proconsi.electrobazar.model.Worker worker) {
        return createSaleWithTariff(lines, paymentMethod, notes, receivedAmount, customer, worker, null);
    }

    /**
     * Creates a sale with an explicit tariff override.
     * If {@code tariffOverride} is null, the customer's own tariff is used
     * (or MINORISTA if the customer has none).
     */
    @Override
    public Sale createSaleWithTariff(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            BigDecimal receivedAmount, com.proconsi.electrobazar.model.Customer customer,
            com.proconsi.electrobazar.model.Worker worker, Tariff tariffOverride) {

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Una venta debe tener al menos un producto.");
        }

        // ── Resolve effective tariff ────────────────────────────────────────
        Tariff effectiveTariff;
        if (tariffOverride != null) {
            effectiveTariff = tariffOverride;
        } else if (customer != null && customer.getTariff() != null) {
            effectiveTariff = customer.getTariff();
        } else {
            // Default: MINORISTA (no discount)
            effectiveTariff = tariffRepository.findByName(Tariff.MINORISTA)
                    .orElse(null);
        }

        BigDecimal discountPct = (effectiveTariff != null && effectiveTariff.getDiscountPercentage() != null)
                ? effectiveTariff.getDiscountPercentage()
                : BigDecimal.ZERO;

        String tariffName = effectiveTariff != null ? effectiveTariff.getName() : Tariff.MINORISTA;

        // Reduce stock before creating the sale
        for (SaleLine line : lines) {
            productService.decreaseStock(line.getProduct().getId(), line.getQuantity());
        }

        // Determine if RE applies for this sale
        boolean applyRecargo = customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());

        // ── Calculate line totals — unit prices are already final (set by the frontend) ──
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal totalRecargo = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO; // kept for record; prices are pre-discounted

        for (SaleLine line : lines) {
            BigDecimal finalPrice = line.getUnitPrice().setScale(SCALE, ROUNDING);

            // Record original as the price received (already final — no further discount)
            line.setOriginalUnitPrice(finalPrice);
            line.setDiscountPercentage(BigDecimal.ZERO);
            line.setUnitPrice(finalPrice);

            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate()
                    : (line.getProduct() != null && line.getProduct().getTaxRate() != null
                            && line.getProduct().getTaxRate().getVatRate() != null
                                    ? line.getProduct().getTaxRate().getVatRate()
                                    : new BigDecimal("0.21"));

            com.proconsi.electrobazar.dto.TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(),
                    line.getProduct().getName(),
                    finalPrice,
                    line.getQuantity(),
                    vatRate,
                    applyRecargo);

            line.setBasePriceNet(breakdown.getUnitPrice());
            line.setBaseAmount(breakdown.getBaseAmount());
            line.setVatAmount(breakdown.getVatAmount());
            line.setRecargoRate(breakdown.getRecargoRate());
            line.setRecargoAmount(breakdown.getRecargoAmount());
            // Total for this line MUST match frontend: (unitPrice * quantity) + recargoAmount
            BigDecimal lineTotal = finalPrice.multiply(BigDecimal.valueOf(line.getQuantity())).setScale(SCALE, ROUNDING)
                    .add(line.getRecargoAmount());
            line.setSubtotal(lineTotal);

            total = total.add(lineTotal);
            totalBase = totalBase.add(line.getBaseAmount());
            totalVat = totalVat.add(line.getVatAmount());
            totalRecargo = totalRecargo.add(line.getRecargoAmount());
        }

        BigDecimal finalTotal = total.setScale(SCALE, ROUNDING);
        BigDecimal changeAmount = null;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal cardAmount = BigDecimal.ZERO;

        if (paymentMethod == PaymentMethod.CASH) {
            cashAmount = finalTotal;
            if (receivedAmount != null) {
                changeAmount = receivedAmount.subtract(finalTotal);
                if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("La cantidad recibida es menor que el total de la venta.");
                }
            }
        } else if (paymentMethod == PaymentMethod.CARD) {
            cardAmount = finalTotal;
        }

        // Construir la venta
        Sale sale = Sale.builder()
                .paymentMethod(paymentMethod)
                .totalAmount(finalTotal)
                .totalBase(totalBase.setScale(SCALE, ROUNDING))
                .totalVat(totalVat.setScale(SCALE, ROUNDING))
                .totalRecargo(totalRecargo.setScale(SCALE, ROUNDING))
                .totalDiscount(totalDiscount.setScale(SCALE, ROUNDING))
                .applyRecargo(applyRecargo)
                .receivedAmount(receivedAmount)
                .changeAmount(changeAmount)
                .cashAmount(cashAmount)
                .cardAmount(cardAmount)
                .notes(notes)
                .customer(customer)
                .worker(worker)
                .lines(lines)
                .appliedTariff(tariffName)
                .appliedDiscountPercentage(discountPct.setScale(SCALE, ROUNDING))
                .build();

        // Link each line to the sale
        lines.forEach(line -> line.setSale(sale));

        Sale savedSale = saleRepository.save(sale);

        String username = worker != null ? worker.getUsername() : "Anónimo";
        activityLogService.logActivity(
                "VENTA",
                "Venta realizada por " + username + " (Total: " + finalTotal
                        + " €, Tarifa: " + tariffName + ")",
                username,
                "SALE",
                savedSale.getId());

        return savedSale;
    }

    @Override
    public Sale createMixedSale(List<SaleLine> lines, String notes, BigDecimal cashAmount, BigDecimal cardAmount,
                                BigDecimal receivedCashAmount, com.proconsi.electrobazar.model.Customer customer,
                                com.proconsi.electrobazar.model.Worker worker) {
        
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Una venta debe tener al menos un producto.");
        }

        // Resolve effective tariff (Default: MINORISTA)
        Tariff effectiveTariff = (customer != null && customer.getTariff() != null) ? customer.getTariff() :
                tariffRepository.findByName(Tariff.MINORISTA).orElse(null);

        BigDecimal discountPct = (effectiveTariff != null && effectiveTariff.getDiscountPercentage() != null)
                ? effectiveTariff.getDiscountPercentage() : BigDecimal.ZERO;
        String tariffName = effectiveTariff != null ? effectiveTariff.getName() : Tariff.MINORISTA;

        // Decrease stock
        for (SaleLine line : lines) {
            productService.decreaseStock(line.getProduct().getId(), line.getQuantity());
        }

        boolean applyRecargo = customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal totalRecargo = BigDecimal.ZERO;

        for (SaleLine line : lines) {
            BigDecimal finalPrice = line.getUnitPrice().setScale(SCALE, ROUNDING);
            line.setOriginalUnitPrice(finalPrice);
            line.setDiscountPercentage(BigDecimal.ZERO);
            line.setUnitPrice(finalPrice);

            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() :
                    (line.getProduct() != null && line.getProduct().getTaxRate() != null ? 
                        line.getProduct().getTaxRate().getVatRate() : new BigDecimal("0.21"));

            com.proconsi.electrobazar.dto.TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(), line.getProduct().getName(), finalPrice, line.getQuantity(), vatRate, applyRecargo);

            line.setBasePriceNet(breakdown.getUnitPrice());
            line.setBaseAmount(breakdown.getBaseAmount());
            line.setVatAmount(breakdown.getVatAmount());
            line.setRecargoRate(breakdown.getRecargoRate());
            line.setRecargoAmount(breakdown.getRecargoAmount());
            BigDecimal lineTotal = finalPrice.multiply(BigDecimal.valueOf(line.getQuantity())).setScale(SCALE, ROUNDING).add(line.getRecargoAmount());
            line.setSubtotal(lineTotal);

            total = total.add(lineTotal);
            totalBase = totalBase.add(line.getBaseAmount());
            totalVat = totalVat.add(line.getVatAmount());
            totalRecargo = totalRecargo.add(line.getRecargoAmount());
        }

        BigDecimal finalTotal = total.setScale(SCALE, ROUNDING);
        // Validation for MIXED payment
        BigDecimal sum = cashAmount.add(cardAmount).setScale(SCALE, ROUNDING);
        if (sum.compareTo(finalTotal) != 0) {
            throw new IllegalArgumentException("La suma de efectivo (" + cashAmount + ") y tarjeta (" + cardAmount + 
                    ") debe ser igual al total (" + finalTotal + ").");
        }

        BigDecimal changeAmount = (receivedCashAmount != null) ? receivedCashAmount.subtract(cashAmount) : BigDecimal.ZERO;
        if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El efectivo entregado es menor que la parte de efectivo asignada.");
        }

        Sale sale = Sale.builder()
                .paymentMethod(PaymentMethod.MIXED)
                .totalAmount(finalTotal)
                .totalBase(totalBase.setScale(SCALE, ROUNDING))
                .totalVat(totalVat.setScale(SCALE, ROUNDING))
                .totalRecargo(totalRecargo.setScale(SCALE, ROUNDING))
                .totalDiscount(BigDecimal.ZERO)
                .applyRecargo(applyRecargo)
                .receivedAmount(receivedCashAmount)
                .changeAmount(changeAmount)
                .cashAmount(cashAmount)
                .cardAmount(cardAmount)
                .notes(notes)
                .customer(customer)
                .worker(worker)
                .lines(lines)
                .appliedTariff(tariffName)
                .appliedDiscountPercentage(discountPct.setScale(SCALE, ROUNDING))
                .build();

        lines.forEach(line -> line.setSale(sale));
        Sale savedSale = saleRepository.save(sale);

        String username = worker != null ? worker.getUsername() : "Anónimo";
        activityLogService.logActivity("VENTA", "Venta MIXTA realizada por " + username + " (Cash: " + cashAmount + 
                " €, Card: " + cardAmount + " €, Total: " + finalTotal + ")", username, "SALE", savedSale.getId());

        return savedSale;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(today.atStartOfDay());

        LocalDateTime endOfDay = today.atStartOfDay().plusDays(1).minusNanos(1);
        return saleRepository.sumTotalBetween(startTime, endOfDay);
    }

    @Override
    @Transactional(readOnly = true)
    public long countToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(today.atStartOfDay());

        LocalDateTime endOfDay = today.atStartOfDay().plusDays(1).minusNanos(1);
        return saleRepository.countByCreatedAtBetween(startTime, endOfDay);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalByPaymentMethodToday(PaymentMethod paymentMethod) {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(today.atStartOfDay());

        LocalDateTime endOfDay = today.atStartOfDay().plusDays(1).minusNanos(1);
        return saleRepository.sumTotalBetweenByPaymentMethod(startTime, endOfDay, paymentMethod)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public com.proconsi.electrobazar.dto.SaleSummaryResponse getSummaryToday() {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .filter(cr -> cr.getOpeningTime() != null)
                .map(cr -> cr.getOpeningTime())
                .orElse(today.atStartOfDay());

        LocalDateTime endOfDay = today.atStartOfDay().plusDays(1).minusNanos(1);
        return saleRepository.getSummaryBetween(startTime, endOfDay);
    }

    @Override
    @Transactional
    public void saveApplyRecargo(Long saleId, boolean applyRecargo) {
        saleRepository.findById(saleId).ifPresent(sale -> {
            boolean old = sale.isApplyRecargo();
            if (old != applyRecargo) {
                sale.setApplyRecargo(applyRecargo);
                saleRepository.save(sale);
                activityLogService.logActivity("MODIFICAR_RECARGO", 
                    "Recargo de equivalencia modificado en Venta #" + saleId + (applyRecargo ? " (Activado)" : " (Desactivado)"), 
                    "Admin", "SALE", saleId);
            }
        });
    }

    @Override
    @Transactional
    public void cancelSale(Long id, com.proconsi.electrobazar.model.Worker worker, String reason) {
        Sale sale = findById(id);
        if (sale.getStatus() == Sale.SaleStatus.CANCELLED) {
            throw new IllegalStateException("La venta ya está anulada.");
        }

        // Restore stock
        for (SaleLine line : sale.getLines()) {
            productService.increaseStock(line.getProduct().getId(), line.getQuantity());
        }

        sale.setStatus(Sale.SaleStatus.CANCELLED);
        sale.setNotes((sale.getNotes() != null ? sale.getNotes() + " | " : "") + "ANULADA: " + reason);
        saleRepository.save(sale);

        String username = worker != null ? worker.getUsername() : "Sistema";
        activityLogService.logActivity(
                "ANULAR_VENTA",
                String.format("Venta #%d anulada por %s. Motivo: %s", id, username, reason),
                username,
                "SALE",
                id);
    }
}