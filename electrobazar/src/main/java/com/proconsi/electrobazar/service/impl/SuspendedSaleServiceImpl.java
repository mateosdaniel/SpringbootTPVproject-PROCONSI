package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.SuspendedSaleLineRequest;
import com.proconsi.electrobazar.model.Product;
import com.proconsi.electrobazar.model.SuspendedSale;
import com.proconsi.electrobazar.model.SuspendedSaleLine;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.repository.SuspendedSaleRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SuspendedSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link SuspendedSaleService}.
 * Manages the "Parking" or "Suspend" feature, allowing incomplete sales to be stored
 * and retrieved later (e.g., when a customer forgets their wallet).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuspendedSaleServiceImpl implements SuspendedSaleService {

    private final SuspendedSaleRepository suspendedSaleRepository;
    private final ProductService productService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public SuspendedSale suspend(List<SuspendedSaleLineRequest> lineRequests, String label, Worker worker) {
        if (lineRequests == null || lineRequests.isEmpty()) {
            throw new IllegalArgumentException("Cannot suspend an empty cart.");
        }

        SuspendedSale sale = SuspendedSale.builder()
                .worker(worker)
                .label(label != null && !label.isBlank() ? label.trim() : null)
                .status(SuspendedSale.SuspendedSaleStatus.SUSPENDED)
                .build();

        List<SuspendedSaleLine> lines = new ArrayList<>();
        for (SuspendedSaleLineRequest req : lineRequests) {
            Product product = productService.findById(req.getProductId());
            lines.add(SuspendedSaleLine.builder()
                    .suspendedSale(sale).product(product).quantity(req.getQuantity()).unitPrice(req.getUnitPrice()).build());
        }
        sale.setLines(lines);

        SuspendedSale saved = suspendedSaleRepository.save(sale);
        
        String logLabel = (label != null) ? label : "Unlabeled";
        activityLogService.logActivity("SUSPENDER_VENTA", 
                String.format("Sale parked: %s with %d item(s)", logLabel, lines.size()), 
                (worker != null ? worker.getUsername() : "Anonymous"), "SALE", null);

        return saved;
    }

    @Override
    @Transactional
    public SuspendedSale resume(Long id, Worker worker) {
        SuspendedSale sale = suspendedSaleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Suspended sale #" + id + " not found."));
        if (sale.getStatus() != SuspendedSale.SuspendedSaleStatus.SUSPENDED) {
            throw new IllegalStateException("Sale is already " + sale.getStatus());
        }

        sale.setStatus(SuspendedSale.SuspendedSaleStatus.RESUMED);
        SuspendedSale saved = suspendedSaleRepository.save(sale);
        
        activityLogService.logActivity("RECUPERAR_VENTA", "Parked sale #" + id + " resumed.", 
                (worker != null ? worker.getUsername() : "Anonymous"), "SALE", id);

        return saved;
    }

    @Override
    @Transactional
    public SuspendedSale cancel(Long id, Worker worker) {
        SuspendedSale sale = suspendedSaleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Suspended sale #" + id + " not found."));
        if (sale.getStatus() != SuspendedSale.SuspendedSaleStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot cancel a sale that is already " + sale.getStatus());
        }

        sale.setStatus(SuspendedSale.SuspendedSaleStatus.CANCELLED);
        SuspendedSale saved = suspendedSaleRepository.save(sale);
        
        activityLogService.logActivity("CANCELAR_VENTA_ESPERA", "Parked sale #" + id + " discarded/cancelled.", 
                (worker != null ? worker.getUsername() : "Anonymous"), "SALE", id);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SuspendedSale> findAllSuspended() {
        return suspendedSaleRepository.findByStatusOrderByCreatedAtDesc(SuspendedSale.SuspendedSaleStatus.SUSPENDED);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SuspendedSale> findById(Long id) {
        return suspendedSaleRepository.findById(id);
    }
}


