package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;

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
    public Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Una venta debe tener al menos un producto.");
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
                .lines(lines)
                .build();

        // Enlazar cada línea con la venta
        lines.forEach(line -> line.setSale(sale));

        return saleRepository.save(sale);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumTotalToday() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusNanos(1);
        return saleRepository.sumTotalBetween(startOfDay, endOfDay);
    }

    @Override
    @Transactional(readOnly = true)
    public long countToday() {
        return saleRepository.countToday();
    }
}