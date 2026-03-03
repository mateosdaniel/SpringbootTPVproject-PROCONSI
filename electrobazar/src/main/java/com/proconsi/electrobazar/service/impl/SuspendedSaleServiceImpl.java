package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.SuspendedSaleLineRequest;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.SuspendedSale;
import com.proconsi.electrobazar.model.SuspendedSaleLine;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.repository.SuspendedSaleRepository;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SuspendedSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuspendedSaleServiceImpl implements SuspendedSaleService {

    private final SuspendedSaleRepository suspendedSaleRepository;
    private final ProductService productService;

    @Override
    @Transactional
    public SuspendedSale suspend(List<SuspendedSaleLineRequest> lineRequests, String label, Worker worker) {
        if (lineRequests == null || lineRequests.isEmpty()) {
            throw new IllegalArgumentException("Cannot suspend a sale with an empty cart.");
        }

        SuspendedSale sale = SuspendedSale.builder()
                .worker(worker)
                .label(label != null && !label.isBlank() ? label.trim() : null)
                .status(SuspendedSale.SuspendedSaleStatus.SUSPENDED)
                .build();

        List<SuspendedSaleLine> lines = new ArrayList<>();
        for (SuspendedSaleLineRequest req : lineRequests) {
            Product product = productService.findById(req.getProductId());
            SuspendedSaleLine line = SuspendedSaleLine.builder()
                    .suspendedSale(sale)
                    .product(product)
                    .quantity(req.getQuantity())
                    .unitPrice(req.getUnitPrice())
                    .build();
            lines.add(line);
        }
        sale.setLines(lines);

        SuspendedSale saved = suspendedSaleRepository.save(sale);
        log.info("Sale suspended (id={}) by worker '{}' with {} line(s)",
                saved.getId(), worker != null ? worker.getUsername() : "?", lines.size());
        return saved;
    }

    @Override
    @Transactional
    public SuspendedSale resume(Long id, Worker worker) {
        SuspendedSale sale = suspendedSaleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suspended sale not found: " + id));

        if (sale.getStatus() != SuspendedSale.SuspendedSaleStatus.SUSPENDED) {
            throw new IllegalStateException("Sale " + id + " is not in SUSPENDED status: " + sale.getStatus());
        }

        sale.setStatus(SuspendedSale.SuspendedSaleStatus.RESUMED);
        SuspendedSale saved = suspendedSaleRepository.save(sale);
        log.info("Sale {} resumed by worker '{}'", id, worker != null ? worker.getUsername() : "?");
        return saved;
    }

    @Override
    @Transactional
    public SuspendedSale cancel(Long id, Worker worker) {
        SuspendedSale sale = suspendedSaleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Suspended sale not found: " + id));

        if (sale.getStatus() != SuspendedSale.SuspendedSaleStatus.SUSPENDED) {
            throw new IllegalStateException("Sale " + id + " is not in SUSPENDED status: " + sale.getStatus());
        }

        sale.setStatus(SuspendedSale.SuspendedSaleStatus.CANCELLED);
        SuspendedSale saved = suspendedSaleRepository.save(sale);
        log.info("Sale {} cancelled by worker '{}'", id, worker != null ? worker.getUsername() : "?");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuspendedSale> findAllSuspended() {
        return suspendedSaleRepository.findByStatusOrderByCreatedAtDesc(
                SuspendedSale.SuspendedSaleStatus.SUSPENDED);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SuspendedSale> findById(Long id) {
        return suspendedSaleRepository.findById(id);
    }
}
