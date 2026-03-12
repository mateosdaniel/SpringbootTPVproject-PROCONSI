package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.SaleWithTaxRequest;
import com.proconsi.electrobazar.dto.SaleWithTaxResponse;
import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.service.ProductPriceService;
import com.proconsi.electrobazar.service.ProductService;
import com.proconsi.electrobazar.service.SaleService;
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
 *
 * <p>This controller integrates the temporal pricing system with the
 * Recargo de Equivalencia (RE) tax calculation to produce a complete
 * tax-aware sale response.</p>
 *
 * <p>Base path: {@code /api/sales/with-tax}</p>
 *
 * <h3>Tax Calculation Flow:</h3>
 * <ol>
 * <li>For each line item, the current active price is fetched from the
 * temporal pricing system (with caching).</li>
 * <li>If the customer has {@code hasRecargoEquivalencia=true}, the RE surcharge
 * is applied on top of the standard VAT.</li>
 * <li>The sale is persisted using the existing {@link SaleService}.</li>
 * <li>A detailed {@link SaleWithTaxResponse} is returned with per-line
 * breakdowns
 * and aggregated totals.</li>
 * </ol>
 *
 * <h3>Example Request:</h3>
 * <pre>{@code
 * POST /api/sales/with-tax
 * {
 * "customerId": 5,
 * "paymentMethod": "CASH",
 * "receivedAmount": 100.00,
 * "workerId": 1,
 * "lines": [
 * { "productId": 10, "quantity": 2 },
 * { "productId": 15, "quantity": 1, "overridePrice": 49.99 }
 * ]
 * }
 * }</pre>
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

        /**
         * Processes a sale with full VAT and optional Recargo de Equivalencia
         * calculation.
         *
         * <p>
         * The endpoint:
         * </p>
         * <ol>
         * <li>Resolves the customer (if provided) and checks RE eligibility.</li>
         * <li>For each line, resolves the active price from the temporal pricing
         * system.</li>
         * <li>Calculates the tax breakdown per line using
         * {@link RecargoEquivalenciaCalculator}.</li>
         * <li>Persists the sale via {@link SaleService#createSale}.</li>
         * <li>Returns a {@link SaleWithTaxResponse} with full tax details.</li>
         * </ol>
         *
         * @param request the sale request with customer, payment, and line items
         * @return 201 with the complete {@link SaleWithTaxResponse}
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

                        // Resolve price: use override if provided, otherwise use temporal pricing
                        // system
                        BigDecimal unitPrice;
                        BigDecimal vatRate;

                        if (lineReq.getOverridePrice() != null) {
                                unitPrice = lineReq.getOverridePrice();
                                // For overridden prices, use the product's current active price VAT rate if
                                // available
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
                                        // Fallback to the product's base price if no temporal price is configured
                                        unitPrice = product.getPrice();
                                        vatRate = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null ? product.getTaxRate().getVatRate()
                                                        : new BigDecimal("0.21"); // Default Spanish standard VAT rate
                                        log.warn("No temporal price found for product '{}' (id={}). Using base price: {}",
                                                        product.getName(), product.getId(), unitPrice);
                                }
                        }

                        // Calculate tax breakdown for this line
                        TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                                        product.getId(),
                                        product.getName(),
                                        unitPrice,
                                        lineReq.getQuantity(),
                                        vatRate,
                                        applyRecargo);

                        taxBreakdowns.add(breakdown);

                        // Build the SaleLine for persistence (uses base price, taxes are informational)
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

                // Note: SaleServiceImpl will now handle setting all breakdown fields (base,
                // vat, recargo)
                // and calculating the correct subtotal for each line based on the input
                // unitPrice (Gross)
                // and whether RE applies.

                // ── 5. Persist the sale ───────────────────────────────────────────────
                Sale savedSale;
                if (request.getPaymentMethod() == PaymentMethod.MIXED) {
                        savedSale = saleService.createMixedSale(
                                        saleLines,
                                        request.getNotes(),
                                        request.getCashAmount(),
                                        request.getCardAmount(),
                                        request.getReceivedAmount(),
                                        customer,
                                        worker);
                } else {
                        savedSale = saleService.createSale(
                                        saleLines,
                                        request.getPaymentMethod(),
                                        request.getNotes(),
                                        request.getReceivedAmount(),
                                        customer,
                                        worker);
                }

                // ── 6. Build and return the response ──────────────────────────────────
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

                log.info("Sale with tax processed: saleId={}, grandTotal={} €, RE applied={}",
                                savedSale.getId(), grandTotal, applyRecargo);

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        /**
         * Calculates a tax preview for a sale without persisting it.
         * Useful for front-end price display before the customer confirms the purchase.
         *
         * <p>
         * Example: {@code POST /api/sales/with-tax/preview}
         * </p>
         *
         * @param request the sale request (same format as the main endpoint)
         * @return 200 with the tax breakdown preview (no sale is created)
         */
        @PostMapping("/preview")
        public ResponseEntity<SaleWithTaxResponse> previewSaleWithTax(
                        @RequestBody SaleWithTaxRequest request) {

                if (request.getLines() == null || request.getLines().isEmpty()) {
                        throw new IllegalArgumentException("La venta debe tener al menos una línea de producto.");
                }

                // Resolve customer RE eligibility
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

                SaleWithTaxResponse preview = SaleWithTaxResponse.builder()
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
                                .build();

                return ResponseEntity.ok(preview);
        }
}
