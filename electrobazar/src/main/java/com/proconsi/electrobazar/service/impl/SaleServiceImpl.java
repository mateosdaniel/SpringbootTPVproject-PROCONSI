package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final CashRegisterRepository cashRegisterRepository;
    private final ActivityLogService activityLogService;
    private final com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator recargoCalculator;

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
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Una venta debe tener al menos un producto.");
        }

        // Validar stock disponible antes de crear la venta
        for (SaleLine line : lines) {
            productService.decreaseStock(line.getProduct().getId(), line.getQuantity());
        }

        // Determine if RE applies for this sale
        boolean applyRecargo = customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());

        // Calcular subtotales y total usando el calculador de impuestos
        BigDecimal total = BigDecimal.ZERO;
        for (SaleLine line : lines) {
            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            com.proconsi.electrobazar.dto.TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                    line.getProduct().getId(),
                    line.getProduct().getName(),
                    line.getUnitPrice(), // unitPrice is Gross (VAT incl)
                    line.getQuantity(),
                    vatRate,
                    applyRecargo);

            line.setSubtotal(breakdown.getTotalAmount());
            total = total.add(line.getSubtotal());
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
                .totalAmount(total)
                .receivedAmount(receivedAmount)
                .changeAmount(changeAmount)
                .notes(notes)
                .customer(customer)
                .worker(worker)
                .lines(lines)
                .build();

        // Enlazar cada línea con la venta
        lines.forEach(line -> line.setSale(sale));

        Sale savedSale = saleRepository.save(sale);

        String username = worker != null ? worker.getUsername() : "Anónimo";
        activityLogService.logActivity(
                "VENTA",
                "Venta realizada por " + username + " (Total: " + total.setScale(2, java.math.RoundingMode.HALF_UP)
                        + " \u20ac)",
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
}