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

        // ── Calculate line totals with discount applied ─────────────────────
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        BigDecimal totalRecargo = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (SaleLine line : lines) {
            // Catalogue (original) gross price
            BigDecimal originalPrice = line.getUnitPrice();
            line.setOriginalUnitPrice(originalPrice.setScale(SCALE, ROUNDING));
            line.setDiscountPercentage(discountPct.setScale(SCALE, ROUNDING));

            // Apply discount to gross price
            BigDecimal discountedPrice;
            if (discountPct.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountFactor = BigDecimal.ONE
                        .subtract(discountPct.divide(new BigDecimal("100"), 10, ROUNDING));
                discountedPrice = originalPrice.multiply(discountFactor).setScale(SCALE, ROUNDING);
            } else {
                discountedPrice = originalPrice.setScale(SCALE, ROUNDING);
            }
            line.setUnitPrice(discountedPrice);

            // The discount Amount to track and display MUST be the net amount (without
            // VAT), to show the true loss of revenue/value.
            // Using a simple reverse VAT calculation to get the net discount
            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate()
                    : (line.getProduct() != null && line.getProduct().getIvaRate() != null
                            ? line.getProduct().getIvaRate()
                            : new BigDecimal("0.21"));
            BigDecimal perUnitGrossDiscount = originalPrice.subtract(discountedPrice);
            BigDecimal divisor = BigDecimal.ONE.add(vatRate);
            BigDecimal perUnitNetDiscount = perUnitGrossDiscount.divide(divisor, 10, ROUNDING).setScale(SCALE,
                    ROUNDING);
            totalDiscount = totalDiscount.add(perUnitNetDiscount.multiply(new BigDecimal(line.getQuantity())));

            com.proconsi.electrobazar.dto.TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(),
                    line.getProduct().getName(),
                    discountedPrice, // use discounted price for tax calculation
                    line.getQuantity(),
                    vatRate,
                    applyRecargo);

            line.setBasePriceNet(breakdown.getUnitPrice());
            line.setBaseAmount(breakdown.getBaseAmount());
            line.setVatAmount(breakdown.getVatAmount());
            line.setRecargoRate(breakdown.getRecargoRate());
            line.setRecargoAmount(breakdown.getRecargoAmount());
            line.setSubtotal(breakdown.getTotalAmount());

            total = total.add(line.getSubtotal());
            totalBase = totalBase.add(line.getBaseAmount());
            totalVat = totalVat.add(line.getVatAmount());
            totalRecargo = totalRecargo.add(line.getRecargoAmount());
        }

        BigDecimal changeAmount = null;
        if (paymentMethod == PaymentMethod.CASH && receivedAmount != null) {
            changeAmount = receivedAmount.subtract(total);
            if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La cantidad recibida es menor que el total de la venta.");
            }
        }

        // Construir la venta
        Sale sale = Sale.builder()
                .paymentMethod(paymentMethod)
                .totalAmount(total.setScale(SCALE, ROUNDING))
                .totalBase(totalBase.setScale(SCALE, ROUNDING))
                .totalVat(totalVat.setScale(SCALE, ROUNDING))
                .totalRecargo(totalRecargo.setScale(SCALE, ROUNDING))
                .totalDiscount(totalDiscount.setScale(SCALE, ROUNDING))
                .applyRecargo(applyRecargo)
                .receivedAmount(receivedAmount)
                .changeAmount(changeAmount)
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
                "Venta realizada por " + username + " (Total: " + total.setScale(SCALE, ROUNDING)
                        + " €, Tarifa: " + tariffName + ")",
                username,
                "SALE",
                savedSale.getId());

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
            sale.setApplyRecargo(applyRecargo);
            saleRepository.save(sale);
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