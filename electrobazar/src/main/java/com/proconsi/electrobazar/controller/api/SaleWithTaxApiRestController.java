package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.SaleWithTaxRequest;
import com.proconsi.electrobazar.dto.SaleWithTaxResponse;
import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.ProductPriceService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.service.TicketService;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for processing sales with full Spanish tax calculation.
 * This controller integrates the temporal pricing system with the
 * Recargo de Equivalencia (RE) surcharge logic for specialized B2B customers.
 *
 * Tax Calculation Flow:
 * 1. Resolves prices from the temporal system (ProductPriceService).
 * 2. Checks Customer RE eligibility (hasRecargoEquivalencia).
 * 3. Calculates tax breakdowns per line (VAT + optional RE).
 * 4. Persists the sale and generates the official document (Invoice or Ticket).
 */
@Slf4j
@RestController
@RequestMapping("/api/sales/with-tax")
@RequiredArgsConstructor
public class SaleWithTaxApiRestController {

        private static final int MONETARY_SCALE = 2;
        private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

        private final SaleService saleService;
        private final ProductService productService;
        private final ProductPriceService productPriceService;
        private final CustomerRepository customerRepository;
        private final WorkerService workerService;
        private final RecargoEquivalenciaCalculator recargoCalculator;
        private final InvoiceService invoiceService;
        private final TicketService ticketService;

        /**
         * Finalizes a sale with detailed tax calculations and persistence.
         * Resolves current prices, calculates VAT/RE breakdowns, and persists the transaction.
         * 
         * @param request Sale details including customer, lines, and payment info.
         * @return 201 Created with the full Sale details including tax breakdowns.
         */
        @PostMapping
        public ResponseEntity<SaleWithTaxResponse> processSaleWithTax(
                        @RequestBody SaleWithTaxRequest request) {

                if (request.getLines() == null || request.getLines().isEmpty()) {
                        throw new IllegalArgumentException("La venta debe tener al menos una línea de producto.");
                }

                // ── 1. Resolve customer and RE eligibility ─────────────────────────────
                Customer customer = null;
                boolean applyRecargo = false;

                if (request.getCustomerId() != null) {
                        customer = customerRepository.findById(request.getCustomerId())
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                         "Cliente no encontrado con id: " + request.getCustomerId()));
                        applyRecargo = Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());
                        log.debug("Customer '{}' (id={}): hasRecargoEquivalencia={}",
                                        customer.getName(), customer.getId(), applyRecargo);
                }

                // ── 2. Resolve worker ──────────────────────────────────────────────────
                Worker worker = null;
                if (request.getWorkerId() != null) {
                        worker = workerService.findById(request.getWorkerId()).orElse(null);
                }

                // ── 3. Process each line item ──────────────────────────────────────────
                LocalDateTime now = LocalDateTime.now();
                List<TaxBreakdown> taxBreakdowns = new ArrayList<>();
                List<SaleLine> saleLines = new ArrayList<>();

                for (SaleWithTaxRequest.SaleLineRequest lineReq : request.getLines()) {
                        Product product = productService.findById(lineReq.getProductId());

                        BigDecimal unitPrice;
                        BigDecimal vatRate;

                        if (lineReq.getOverridePrice() != null) {
                                unitPrice = lineReq.getOverridePrice();
                                ProductPrice activePrice = productPriceService.getCurrentPrice(product.getId(), now);
                                vatRate = activePrice != null ? activePrice.getVatRate()
                                                : (product.getTaxRate() != null && product.getTaxRate().getVatRate() != null ? product.getTaxRate().getVatRate()
                                                                : new BigDecimal("0.21"));
                                log.debug("Using override price {} for product '{}' (id={})",
                                                unitPrice, product.getName(), product.getId());
                        } else {
                                ProductPrice activePrice = productPriceService.getCurrentPrice(product.getId(), now);
                                if (activePrice != null) {
                                        unitPrice = activePrice.getPrice();
                                        vatRate = activePrice.getVatRate();
                                        log.debug("Using temporal price {} (id={}) for product '{}' (id={})",
                                                        unitPrice, activePrice.getId(), product.getName(),
                                                        product.getId());
                                } else {
                                        unitPrice = product.getPrice();
                                        vatRate = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null ? product.getTaxRate().getVatRate()
                                                        : new BigDecimal("0.21"); 
                                        log.warn("No temporal price found for product '{}' (id={}). Using base price: {}",
                                                        product.getName(), product.getId(), unitPrice);
                                }
                        }

                        TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                                        product.getId(),
                                        product.getName(),
                                        unitPrice,
                                        lineReq.getQuantity(),
                                        vatRate,
                                        applyRecargo);

                        taxBreakdowns.add(breakdown);

                        SaleLine saleLine = SaleLine.builder()
                                        .product(product)
                                        .quantity(lineReq.getQuantity())
                                        .unitPrice(unitPrice.setScale(MONETARY_SCALE, ROUNDING_MODE))
                                        .vatRate(vatRate)
                                        .build();
                        saleLines.add(saleLine);
                }

                // ── 4. Aggregate totals ────────────────────────────────────────────────
                BigDecimal totalBase = taxBreakdowns.stream()
                                .map(TaxBreakdown::getBaseAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                BigDecimal totalVat = taxBreakdowns.stream()
                                .map(TaxBreakdown::getVatAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                BigDecimal totalRecargo = taxBreakdowns.stream()
                                .map(TaxBreakdown::getRecargoAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                BigDecimal grandTotal = totalBase.add(totalVat).add(totalRecargo)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                // ── 5. Persist the sale ───────────────────────────────────────────────
                Sale savedSale = saleService.createSale(
                                saleLines,
                                request.getPaymentMethod(),
                                request.getNotes(),
                                request.getReceivedAmount(),
                                customer,
                                worker);

                // ── 6. Generate invoice or ticket ─────────────────────────────────────
                boolean requestInvoice = Boolean.TRUE.equals(request.getRequestInvoice());
                Invoice invoice = null;
                try {
                        if (requestInvoice && customer != null) {
                                invoice = invoiceService.createInvoice(savedSale);
                                log.info("Invoice {} generated for saleId={}", invoice.getInvoiceNumber(), savedSale.getId());
                        } else {
                                ticketService.createTicket(savedSale, applyRecargo);
                        }
                } catch (Exception e) {
                        log.error("Error generating document for saleId={}: {}", savedSale.getId(), e.getMessage());
                }

                // ── 7. Build and return the response ──────────────────────────────────
                SaleWithTaxResponse response = SaleWithTaxResponse.builder()
                                .saleId(savedSale.getId())
                                .createdAt(savedSale.getCreatedAt())
                                .customerId(customer != null ? customer.getId() : null)
                                .customerName(customer != null ? customer.getName() : null)
                                .recargoEquivalenciaApplied(applyRecargo)
                                .paymentMethod(savedSale.getPaymentMethod())
                                .receivedAmount(savedSale.getReceivedAmount())
                                .changeAmount(savedSale.getChangeAmount())
                                .lines(taxBreakdowns)
                                .totalBase(totalBase)
                                .totalVat(totalVat)
                                .totalRecargo(totalRecargo)
                                .grandTotal(grandTotal)
                                .notes(savedSale.getNotes())
                                .build();

                log.info("Sale with tax processed: saleId={}, grandTotal={} €, RE applied={}, invoice={}",
                                savedSale.getId(), grandTotal, applyRecargo, invoice != null ? invoice.getInvoiceNumber() : "none");

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        /**
         * Calculates a tax preview for a potential sale without creating it.
         * Used for front-end price display calculations before final confirmation.
         * 
         * @param request Proposed sale details.
         * @return 200 OK with the calculated preview.
         */
        @PostMapping("/preview")
        public ResponseEntity<SaleWithTaxResponse> previewSaleWithTax(
                        @RequestBody SaleWithTaxRequest request) {

                if (request.getLines() == null || request.getLines().isEmpty()) {
                        throw new IllegalArgumentException("La venta debe tener al menos una línea de producto.");
                }

                Customer customer = null;
                boolean applyRecargo = false;

                if (request.getCustomerId() != null) {
                        customer = customerRepository.findById(request.getCustomerId())
                                        .orElseThrow(() -> new ResourceNotFoundException(
                                                         "Cliente no encontrado con id: " + request.getCustomerId()));
                        applyRecargo = Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());
                }

                LocalDateTime now = LocalDateTime.now();
                List<TaxBreakdown> taxBreakdowns = new ArrayList<>();

                for (SaleWithTaxRequest.SaleLineRequest lineReq : request.getLines()) {
                        Product product = productService.findById(lineReq.getProductId());

                        BigDecimal unitPrice;
                        BigDecimal vatRate;

                        if (lineReq.getOverridePrice() != null) {
                                unitPrice = lineReq.getOverridePrice();
                                ProductPrice activePrice = productPriceService.getCurrentPrice(product.getId(), now);
                                vatRate = activePrice != null ? activePrice.getVatRate()
                                                : (product.getTaxRate() != null && product.getTaxRate().getVatRate() != null ? product.getTaxRate().getVatRate()
                                                                : new BigDecimal("0.21"));
                        } else {
                                ProductPrice activePrice = productPriceService.getCurrentPrice(product.getId(), now);
                                if (activePrice != null) {
                                        unitPrice = activePrice.getPrice();
                                        vatRate = activePrice.getVatRate();
                                } else {
                                        unitPrice = product.getPrice();
                                        vatRate = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null ? product.getTaxRate().getVatRate()
                                                        : new BigDecimal("0.21");
                                }
                        }

                        TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                                        product.getId(),
                                        product.getName(),
                                        unitPrice,
                                        lineReq.getQuantity(),
                                        vatRate,
                                        applyRecargo);

                        taxBreakdowns.add(breakdown);
                }

                BigDecimal totalBase = taxBreakdowns.stream()
                                .map(TaxBreakdown::getBaseAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                BigDecimal totalVat = taxBreakdowns.stream()
                                .map(TaxBreakdown::getVatAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                BigDecimal totalRecargo = taxBreakdowns.stream()
                                .map(TaxBreakdown::getRecargoAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                BigDecimal grandTotal = totalBase.add(totalVat).add(totalRecargo)
                                .setScale(MONETARY_SCALE, ROUNDING_MODE);

                return ResponseEntity.ok(SaleWithTaxResponse.builder()
                                .customerId(customer != null ? customer.getId() : null)
                                .customerName(customer != null ? customer.getName() : null)
                                .recargoEquivalenciaApplied(applyRecargo)
                                .paymentMethod(request.getPaymentMethod())
                                .lines(taxBreakdowns)
                                .totalBase(totalBase)
                                .totalVat(totalVat)
                                .totalRecargo(totalRecargo)
                                .grandTotal(grandTotal)
                                .notes(request.getNotes())
                                .build());
        }
}
