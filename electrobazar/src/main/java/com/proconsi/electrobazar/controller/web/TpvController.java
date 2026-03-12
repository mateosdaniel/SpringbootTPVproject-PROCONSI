package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/tpv")
@RequiredArgsConstructor
public class TpvController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SaleService saleService;
    private final CustomerService customerService;
    private final CashRegisterService cashRegisterService;
    private final PdfReportService pdfReportService;
    private final ProductPriceService productPriceService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final InvoiceService invoiceService;
    private final ReturnService returnService;
    private final TicketService ticketService;
    private final CashWithdrawalService cashWithdrawalService;
    private final ActivityLogService activityLogService;
    private final TariffService tariffService;
    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;

    @GetMapping
    public String index(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            HttpSession session,
            Model model) {

        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }

        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("selectedCategoryId", categoryId);

        List<Product> products;
        if (search != null && !search.isBlank()) {
            products = productService.search(search);
        } else if (categoryId != null) {
            products = productService.findByCategory(categoryId);
        } else {
            products = productService.findAllActiveWithCategory();
        }

        java.util.Optional<CashRegister> openRegisterOpt = cashRegisterService.getOpenRegister();
        if (openRegisterOpt.isEmpty()) {
            return "redirect:/tpv/open-register";
        }

        model.addAttribute("products", products);
        model.addAttribute("search", search);
        model.addAttribute("totalToday", saleService.sumTotalToday());
        model.addAttribute("countToday", saleService.countToday());
        model.addAttribute("todayRegister", openRegisterOpt.get());
        model.addAttribute("tariffs", tariffService.findAllActive());

        return "tpv/index";
    }

    @PostMapping("/sale")
    public String processSale(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerType,
            @RequestParam List<Long> productIds,
            @RequestParam List<Integer> quantities,
            @RequestParam(required = false) List<String> unitPrices,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String receivedAmount,
            @RequestParam(required = false, defaultValue = "false") Boolean requestInvoice,
            @RequestParam(required = false) Long tariffId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }

        // Procesar cliente
        Customer customer = null;
        if (customerId != null) {
            customer = customerService.findById(customerId);
        } else if (customerName != null && !customerName.isBlank()) {
            Customer.CustomerType type = (customerType != null && customerType.equals("COMPANY"))
                    ? Customer.CustomerType.COMPANY
                    : Customer.CustomerType.INDIVIDUAL;
            customer = customerService.save(Customer.builder()
                    .name(customerName)
                    .type(type)
                    .build());
        }

        // Resolve tariff override (cashier manual selection)
        Tariff tariffOverride = null;
        if (tariffId != null) {
            tariffOverride = tariffService.findById(tariffId).orElse(null);
        }

        // Determinar si aplica Recargo de Equivalencia
        boolean applyRecargo = customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());

        // Build sale lines using the unit prices sent by the frontend ticket.
        // These are already the final prices (tariff-adjusted by the sidebar) — do NOT recalculate.
        List<SaleLine> lines = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Product product = productService.findById(productIds.get(i));
            int qty = quantities.get(i);

            // Use the price sent from the ticket; fall back to productPriceService only if missing.
            BigDecimal unitPrice;
            BigDecimal vatRate;

            if (unitPrices != null && i < unitPrices.size() && unitPrices.get(i) != null && !unitPrices.get(i).isBlank()) {
                // Price already final from the TPV sidebar — use as-is
                unitPrice = new BigDecimal(unitPrices.get(i).replace(",", "."));
                vatRate = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null
                        ? product.getTaxRate().getVatRate()
                        : new BigDecimal("0.21");
            } else {
                // Fallback: read from price schedule (no tariff discount applied here)
                ProductPrice activePrice = productPriceService.getCurrentPrice(product.getId(), LocalDateTime.now());
                if (activePrice != null) {
                    unitPrice = activePrice.getPrice();
                    vatRate = activePrice.getVatRate();
                } else {
                    unitPrice = product.getPrice();
                    vatRate = product.getTaxRate() != null && product.getTaxRate().getVatRate() != null
                            ? product.getTaxRate().getVatRate()
                            : new BigDecimal("0.21");
                }
            }

            lines.add(SaleLine.builder()
                    .product(product)
                    .quantity(qty)
                    .unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
                    .vatRate(vatRate)
                    .build());
        }

        Worker worker = (Worker) session.getAttribute("worker");

        // Sum total from lines pre-discount just for cash limit validation (approximate
        // but safe since discounts only reduce price)
        BigDecimal maxPosibleAmount = lines.stream()
                .map(l -> l.getUnitPrice().multiply(new BigDecimal(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Validar límite de pago en efectivo (Ley 11/2021)
        if (paymentMethod == PaymentMethod.CASH && maxPosibleAmount.compareTo(new BigDecimal("1000")) >= 0) {
            activityLogService.logActivity("CASH_LIMIT_VIOLATION",
                    "Intento de pago en efectivo bloqueado por importe >= 1000€ (Total estimado: " + maxPosibleAmount
                            + "€)",
                    worker.getUsername(), "SALE", null);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "El pago en efectivo no está permitido para importes iguales o superiores a 1.000 € según la Ley 11/2021 de prevención del fraude fiscal. Seleccione otro método de pago.");
            return "redirect:/tpv";
        }

        BigDecimal receivedAmountDecimal = null;
        if (paymentMethod == PaymentMethod.CASH && receivedAmount != null && !receivedAmount.isBlank()) {
            try {
                receivedAmountDecimal = new BigDecimal(receivedAmount.replace(",", "."));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        Sale sale = saleService.createSaleWithTariff(lines, paymentMethod, notes, receivedAmountDecimal, customer,
                worker, tariffOverride);

        // We no longer put taxBreakdowns, totalBase, etc. in flash attributes.
        // showReceipt now computes them correctly from the finalized Sale object and
        // discounted prices.

        // Generate and Store PDF in DB
        try {
            Invoice invoice = null;
            // Solo creamos la factura si se solicita explícitamente Y hay un cliente
            // seleccionado
            if (Boolean.TRUE.equals(requestInvoice) && customer != null) {
                invoice = invoiceService.createInvoice(sale);
                redirectAttributes.addFlashAttribute("invoice", invoice);
            }

            if (invoice != null) {
                // For invoices: update success message
                redirectAttributes.addFlashAttribute("successMessage",
                        "Factura " + invoice.getInvoiceNumber() + " generada.");
            } else {
                // For tickets: save recargo flag and create ticket record
                saleService.saveApplyRecargo(sale.getId(), applyRecargo);
                ticketService.createTicket(sale, applyRecargo);
            }
        } catch (Exception e) {
            log.error("Error creating document record for sale " + sale.getId(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Venta completada pero hubo un error al generar el documento PDF.");
        }

        return "redirect:/tpv/receipt/" + sale.getId();
    }

    @GetMapping("/receipt/{saleId}")
    public String showReceipt(
            @PathVariable Long saleId,
            @RequestParam(required = false, defaultValue = "false") Boolean autoPrint,
            HttpSession session,
            Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        Sale sale = saleService.findById(saleId);
        model.addAttribute("sale", sale);
        model.addAttribute("autoPrint", autoPrint);

        // Resolve invoice (from flash or DB) — must happen before template decision
        if (!model.containsAttribute("invoice")) {
            invoiceService.findBySaleId(saleId)
                    .ifPresent(inv -> model.addAttribute("invoice", inv));
        }

        // Resolve ticket (only if not an invoice)
        if (!model.containsAttribute("invoice") && !model.containsAttribute("ticket")) {
            ticketService.findBySaleId(saleId)
                    .ifPresent(t -> model.addAttribute("ticket", t));
        }

        // Always ensure tax-breakdown variables are in the model.
        // This runs whether the sale is a ticket OR an invoice, and whether we
        // came from a redirect (flash attrs present) or a direct URL (page reload).
        // tpv/invoice.html references totalBase / totalVat / totalRecargo so they
        // must be populated before we choose the template.
        if (!model.containsAttribute("taxBreakdowns")) {
            boolean applyRecargo = sale.getCustomer() != null
                    && Boolean.TRUE.equals(sale.getCustomer().getHasRecargoEquivalencia());
            List<TaxBreakdown> breakdowns = new ArrayList<>();
            for (SaleLine line : sale.getLines()) {
                BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
                TaxBreakdown bd = recargoCalculator.calculateLineBreakdown(
                        line.getProduct().getId(), line.getProduct().getName(),
                        line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo);
                breakdowns.add(bd);
            }
            model.addAttribute("taxBreakdowns", breakdowns);
            model.addAttribute("applyRecargo", applyRecargo);
            BigDecimal totalBase = breakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalVat = breakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalRecargo = breakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            model.addAttribute("totalBase", totalBase);
            model.addAttribute("totalVat", totalVat);
            model.addAttribute("totalRecargo", totalRecargo);
        }

        // Route to the correct screen template now that the model is fully populated
        if (model.containsAttribute("invoice")) {
            return "tpv/invoice";
        }

        return "tpv/receipt";
    }

    @GetMapping("/cash-close")
    public String cashCloseForm(HttpSession session, Model model) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        if (!worker.getEffectivePermissions().contains("CASH_CLOSE")) {
            return "redirect:/tpv";
        }

        java.util.Optional<CashRegister> openRegisterOpt = cashRegisterService.getOpenRegister();
        if (openRegisterOpt.isEmpty()) {
            return "redirect:/tpv/open-register";
        }

        CashRegister openRegister = openRegisterOpt.get();
        java.math.BigDecimal cashSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CASH);
        java.math.BigDecimal cashRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CASH);

        List<CashWithdrawal> movements = cashWithdrawalService.findByRegisterId(openRegister.getId());
        java.math.BigDecimal totalWithdrawals = movements.stream()
                .filter(m -> m.getType() == null || m.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        java.math.BigDecimal totalEntries = movements.stream()
                .filter(m -> m.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        java.time.LocalDateTime startOfShift = openRegister.getOpeningTime() != null
                ? openRegister.getOpeningTime()
                : java.time.LocalDate.now().atStartOfDay();
        model.addAttribute("returnsToday",
                returnService.findByCreatedAtBetween(startOfShift, java.time.LocalDateTime.now()));

        java.math.BigDecimal expectedCashInDrawer = openRegister.getOpeningBalance()
                .add(cashSalesToday)
                .add(totalEntries)
                .subtract(totalWithdrawals)
                .subtract(cashRefundsToday);

        com.proconsi.electrobazar.dto.SaleSummaryResponse summary = saleService.getSummaryToday();
        model.addAttribute("cancelledCount", summary.getTotalCancelledCount());
        model.addAttribute("cancelledTotal", summary.getTotalCancelledAmount());

        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("totalToday", saleService.sumTotalToday());
        model.addAttribute("countToday", saleService.countToday());
        model.addAttribute("todayRegister", openRegister);
        model.addAttribute("cashSalesToday", cashSalesToday);
        model.addAttribute("cashRefundsToday", cashRefundsToday);
        model.addAttribute("cardSalesToday", saleService.sumTotalByPaymentMethodToday(PaymentMethod.CARD));
        model.addAttribute("cardRefundsToday", returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CARD));
        model.addAttribute("totalWithdrawals", totalWithdrawals);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("expectedCashInDrawer", expectedCashInDrawer);
        return "tpv/cash-close";
    }

    @PostMapping("/withdrawal")
    public String processWithdrawal(
            @RequestParam String amount,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false, defaultValue = "WITHDRAWAL") String type,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        if (!worker.getEffectivePermissions().contains("CASH_CLOSE")) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tiene permiso para realizar movimientos de caja.");
            return "redirect:/tpv";
        }

        try {
            java.util.Optional<CashRegister> openRegister = cashRegisterService.getOpenRegister();
            if (openRegister.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No hay ninguna caja abierta.");
                return "redirect:/tpv";
            }

            BigDecimal amountDecimal = new BigDecimal(amount.replace(",", "."));
            CashWithdrawal.MovementType movementType = CashWithdrawal.MovementType.valueOf(type);

            cashWithdrawalService.processMovement(openRegister.get().getId(), amountDecimal, reason, movementType,
                    worker);

            String msg = (movementType == CashWithdrawal.MovementType.ENTRY ? "Entrada" : "Retirada")
                    + " de " + amountDecimal.setScale(2, RoundingMode.HALF_UP) + " \u20ac realizada correctamente.";
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar el movimiento: " + e.getMessage());
        }

        return "redirect:/tpv";
    }

    @GetMapping("/preferences")
    public String preferences(HttpSession session) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        return "tpv/preferences";
    }

    @GetMapping("/open-register")
    public String openRegisterForm(HttpSession session, Model model) {
        if (session.getAttribute("worker") == null)
            return "redirect:/login";
        if (cashRegisterService.getOpenRegister().isPresent()) {
            return "redirect:/tpv";
        }
        com.proconsi.electrobazar.dto.CashRegisterOpenSuggestion suggestion = cashRegisterService.getOpenSuggestion();
        model.addAttribute("hasSuggestion", suggestion.isHasSuggestion());
        model.addAttribute("suggestedOpeningBalance", suggestion.getSuggestedBalance());
        return "tpv/open-register";
    }

    @PostMapping("/open-register")
    public String processOpenRegister(
            @RequestParam String openingBalance,
            HttpSession session) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        String normalizedBalance = openingBalance.replace(",", ".");
        java.math.BigDecimal openingBalanceDecimal;
        try {
            openingBalanceDecimal = new java.math.BigDecimal(normalizedBalance);
        } catch (NumberFormatException e) {
            openingBalanceDecimal = java.math.BigDecimal.ZERO;
        }

        cashRegisterService.openCashRegister(openingBalanceDecimal, worker);
        return "redirect:/tpv";
    }

    @PostMapping("/cash-close")
    public String processCashClose(
            @RequestParam String closingBalance,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String retainedAmount,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        if (!worker.getEffectivePermissions().contains("CASH_CLOSE")) {
            return "redirect:/tpv";
        }

        String normalizedBalance = closingBalance.replace(",", ".");
        java.math.BigDecimal closingBalanceDecimal = new java.math.BigDecimal(normalizedBalance);

        java.math.BigDecimal retainedAmountDecimal = null;
        if (retainedAmount != null && !retainedAmount.isBlank()) {
            try {
                retainedAmountDecimal = new java.math.BigDecimal(retainedAmount.replace(",", "."));
            } catch (NumberFormatException e) {
                // Ignore malformed values — treat as no retention
            }
        }

        CashRegister register = cashRegisterService.closeCashRegister(
                closingBalanceDecimal, notes, worker, retainedAmountDecimal);

        try {
            // Ya no generamos ni guardamos el PDF aquí. Se regenera al descargar.

            redirectAttributes.addFlashAttribute("successMessage",
                    "Cierre de caja realizado. Diferencia: " + register.getDifference()
                            + "\u20AC. PDF almacenado en base de datos.");
        } catch (Exception e) {
            log.error("Error generating/storing cash close PDF for register " + register.getId(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cierre realizado pero hubo un error al generar el documento PDF.");
        }

        return "redirect:/tpv/open-register";
    }

    @GetMapping("/return/check")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> checkTicketForReturn(@RequestParam String query) {
        try {
            Long saleId = null;
            // 1. Try searching by ID
            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                Sale sale = saleService.findById(id);
                if (sale != null) {
                    saleId = id;
                }
            }

            // 2. Try searching by Ticket Number
            if (saleId == null) {
                saleId = ticketService.findByTicketNumber(query)
                        .map(t -> t.getSale().getId())
                        .orElse(null);
            }

            if (saleId != null) {
                return org.springframework.http.ResponseEntity
                        .ok(java.util.Collections.singletonMap("redirectUrl", "/tpv/return/" + saleId));
            } else {
                return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(java.util.Collections.singletonMap("errorMessage", "Ticket no encontrado: " + query));
            }
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Collections.singletonMap("errorMessage",
                            "Error al buscar el ticket: " + e.getMessage()));
        }
    }

    @GetMapping("/return/search")
    public String searchTicketForReturn(@RequestParam String query, RedirectAttributes redirectAttributes) {
        try {
            // Try searching by ID
            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                if (saleService.findById(id) != null) {
                    return "redirect:/tpv/return/" + id;
                }
            }

            // Try searching by Ticket Number
            return ticketService.findByTicketNumber(query)
                    .map(t -> "redirect:/tpv/return/" + t.getSale().getId())
                    .orElseGet(() -> {
                        redirectAttributes.addFlashAttribute("errorMessage", "Ticket no encontrado: " + query);
                        return "redirect:/tpv";
                    });
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al buscar el ticket: " + e.getMessage());
            return "redirect:/tpv";
        }
    }

    @GetMapping("/return/{saleId}")
    public String showReturnForm(@PathVariable Long saleId, HttpSession session, Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        Sale sale = saleService.findById(saleId);
        model.addAttribute("sale", sale);
        model.addAttribute("paymentMethods", PaymentMethod.values());

        java.util.Map<Long, Integer> alreadyReturned = new java.util.HashMap<>();
        for (SaleLine line : sale.getLines()) {
            int returned = returnService.findByOriginalSaleId(saleId).stream()
                    .flatMap(r -> r.getLines().stream())
                    .filter(rl -> rl.getSaleLine().getId().equals(line.getId()))
                    .mapToInt(com.proconsi.electrobazar.model.ReturnLine::getQuantity)
                    .sum();
            alreadyReturned.put(line.getId(), returned);
        }
        model.addAttribute("alreadyReturned", alreadyReturned);
        return "tpv/return-form";
    }

    @PostMapping("/return")
    public String processReturn(
            @RequestParam Long saleId,
            @RequestParam List<Long> saleLineIds,
            @RequestParam List<Integer> quantities,
            @RequestParam(required = false) String reason,
            @RequestParam PaymentMethod paymentMethod,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return "redirect:/login";
        }

        List<com.proconsi.electrobazar.dto.ReturnLineRequest> lineRequests = new ArrayList<>();
        for (int i = 0; i < saleLineIds.size(); i++) {
            lineRequests.add(new com.proconsi.electrobazar.dto.ReturnLineRequest(
                    saleLineIds.get(i), quantities.get(i)));
        }

        try {
            SaleReturn saleReturn = returnService.processReturn(
                    saleId, lineRequests, reason, paymentMethod, worker);
            redirectAttributes.addFlashAttribute("saleReturn", saleReturn);
            return "redirect:/tpv/return-receipt/" + saleReturn.getId();
        } catch (IllegalArgumentException | com.proconsi.electrobazar.exception.InsufficientCashException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/tpv/return/" + saleId;
        }
    }

    @GetMapping("/return-receipt/{returnId}")
    public String showReturnReceipt(
            @PathVariable Long returnId,
            @RequestParam(required = false, defaultValue = "false") Boolean autoPrint,
            HttpSession session,
            Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        SaleReturn saleReturn = (SaleReturn) model.getAttribute("saleReturn");
        if (saleReturn == null) {
            saleReturn = returnService.findById(returnId)
                    .orElseThrow(() -> new IllegalArgumentException("Return not found: " + returnId));
            model.addAttribute("saleReturn", saleReturn);
        }
        model.addAttribute("autoPrint", autoPrint);

        // Calculate common tax breakdown (positive values for standard receipt)
        Sale originalSale = saleReturn.getOriginalSale();
        boolean applyRecargo = originalSale.isApplyRecargo();
        List<TaxBreakdown> standardBreakdowns = new ArrayList<>();

        for (ReturnLine line : saleReturn.getLines()) {
            if (line.getSaleLine() == null || line.getSaleLine().getProduct() == null)
                continue;

            BigDecimal vatRate = line.getVatRate() != null ? line.getVatRate() : new BigDecimal("0.21");
            TaxBreakdown bd = recargoCalculator.calculateLineBreakdown(
                    line.getSaleLine().getProduct().getId(), line.getSaleLine().getProduct().getName(),
                    line.getUnitPrice(), line.getQuantity(), vatRate, applyRecargo);
            standardBreakdowns.add(bd);
        }

        model.addAttribute("applyRecargo", applyRecargo);
        model.addAttribute("totalBase", standardBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("totalVat", standardBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("totalRecargo", standardBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

        // If the return has a rectificative invoice, prepare negative values and use
        // specific template
        if (saleReturn.getRectificativeInvoice() != null) {
            List<TaxBreakdown> negativeBreakdowns = new ArrayList<>();
            for (TaxBreakdown bd : standardBreakdowns) {
                negativeBreakdowns.add(TaxBreakdown.builder()
                        .productId(bd.getProductId())
                        .productName(bd.getProductName())
                        .unitPrice(bd.getUnitPrice())
                        .quantity(bd.getQuantity() * -1)
                        .baseAmount(bd.getBaseAmount().negate())
                        .vatRate(bd.getVatRate())
                        .vatAmount(bd.getVatAmount().negate())
                        .recargoRate(bd.getRecargoRate())
                        .recargoAmount(bd.getRecargoAmount().negate())
                        .totalAmount(bd.getTotalAmount().negate())
                        .recargoApplied(applyRecargo)
                        .build());
            }
            model.addAttribute("taxBreakdowns", negativeBreakdowns);

            // Re-calculate totals with negative amounts for the invoice
            model.addAttribute("totalBase", negativeBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalVat", negativeBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalRecargo", negativeBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

            // Prepare negative lines for the table
            List<java.util.Map<String, Object>> negativeLines = new ArrayList<>();
            for (ReturnLine line : saleReturn.getLines()) {
                if (line.getSaleLine() == null || line.getSaleLine().getProduct() == null)
                    continue;
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("name", line.getSaleLine().getProduct().getName());
                map.put("unitPrice", line.getUnitPrice());
                map.put("quantity", line.getQuantity() * -1);
                map.put("subtotal", line.getSubtotal().negate());
                map.put("vatRate", line.getVatRate());
                map.put("recargoRate", line.getSaleLine().getRecargoRate());
                negativeLines.add(map);
            }
            model.addAttribute("negativeLines", negativeLines);
            model.addAttribute("totalAmount", saleReturn.getTotalRefunded().negate());

            return "tpv/rectificative-invoice";
        }

        model.addAttribute("taxBreakdowns", standardBreakdowns);
        return "tpv/return-receipt";
    }
    /**
     * Returns the effective unit price for a product, optionally discounted by a tariff.
     * Used by the TPV sidebar to update ticket prices when a customer is selected.
     *
     * <p>Lookup order:
     * <ol>
     *   <li>tariff_price_history WHERE product_id = ? AND tariff_id = ? AND valid_to IS NULL → returns price_with_vat directly</li>
     *   <li>Fallback: basePrice * (1 - tariff.discountPercentage / 100)</li>
     * </ol></p>
     *
     * <p>Example: {@code GET /tpv/api/products/42/price?tariffId=3}</p>
     *
     * @param productId the product ID
     * @param tariffId  optional tariff to apply discount from
     * @return the effective price as a plain decimal number
     */
    @GetMapping("/api/products/{productId}/price")
    @ResponseBody
    public java.util.Map<String, BigDecimal> getProductEffectivePrice(
            @PathVariable Long productId,
            @RequestParam(required = false) Long tariffId) {

        Product product = productService.findById(productId);
        if (product == null) {
            return java.util.Collections.emptyMap();
        }

        // Get current active base price (from ProductPrice scheduling system)
        ProductPrice activePrice = productPriceService.getCurrentPrice(productId, LocalDateTime.now());
        BigDecimal basePrice = (activePrice != null) ? activePrice.getPrice() : product.getPrice();
        BigDecimal vatRate = (activePrice != null) ? activePrice.getVatRate() : 
                (product.getTaxRate() != null && product.getTaxRate().getVatRate() != null 
                ? product.getTaxRate().getVatRate() : new BigDecimal("0.21"));

        BigDecimal finalPrice;
        BigDecimal priceWithRe;

        if (tariffId == null) {
            finalPrice = basePrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);
            BigDecimal netPrice = finalPrice.divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP);
            priceWithRe = netPrice.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(2, RoundingMode.HALF_UP);
        } else {
            // 1. Look up the active price from tariff_price_history first
            var historyEntry = tariffPriceHistoryRepository.findCurrentByProductAndTariff(productId, tariffId);
            if (historyEntry.isPresent()) {
                finalPrice = historyEntry.get().getPriceWithVat();
                priceWithRe = historyEntry.get().getPriceWithRe();
            } else {
                // 2. Fallback: apply tariff discount % to the base price
                finalPrice = tariffService.findById(tariffId)
                        .map(tariff -> {
                            BigDecimal discount = tariff.getDiscountPercentage();
                            if (discount == null || discount.compareTo(BigDecimal.ZERO) == 0) {
                                return basePrice.setScale(2, RoundingMode.HALF_UP);
                            }
                            BigDecimal factor = BigDecimal.ONE.subtract(
                                    discount.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                            return basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
                        })
                        .orElse(basePrice.setScale(2, RoundingMode.HALF_UP));
                
                BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);
                BigDecimal netPrice = finalPrice.divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP);
                priceWithRe = netPrice.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(2, RoundingMode.HALF_UP);
            }
        }

        java.util.Map<String, BigDecimal> response = new java.util.HashMap<>();
        response.put("price", finalPrice);
        response.put("priceWithRe", priceWithRe);
        return response;
    }
}
