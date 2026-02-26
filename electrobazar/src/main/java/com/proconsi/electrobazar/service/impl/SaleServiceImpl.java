package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
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

    @Override
    @Transactional(readOnly = true)
    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada con id: " + id));
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
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            com.proconsi.electrobazar.model.Worker worker) {
        return createSale(lines, paymentMethod, notes, null, worker);
    }

    @Override
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            com.proconsi.electrobazar.model.Customer customer, com.proconsi.electrobazar.model.Worker worker) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Una venta debe tener al menos un producto.");
        }

        // Validar stock disponible antes de crear la venta
        for (SaleLine line : lines) {
            productService.decreaseStock(line.getProduct().getId(), line.getQuantity());
        }

        // Calcular subtotales y total
        BigDecimal total = BigDecimal.ZERO;
        for (SaleLine line : lines) {
            BigDecimal subtotal = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            line.setSubtotal(subtotal);
            total = total.add(subtotal);
        }

        // Construir la venta
        Sale sale = Sale.builder()
                .paymentMethod(paymentMethod)
                .totalAmount(total)
                .notes(notes)
                .customer(customer)
                .worker(worker)
                .lines(lines)
                .build();

        // Enlazar cada línea con la venta
        lines.forEach(line -> line.setSale(sale));

        return saleRepository.save(sale);
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
}