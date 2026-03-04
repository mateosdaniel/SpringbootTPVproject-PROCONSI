package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.model.CashWithdrawal;
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

        return "tpv/index";
    }

    @PostMapping("/sale")
    public String processSale(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerType,
            @RequestParam List<Long> productIds,
            @RequestParam List<Integer> quantities,
            @RequestParam PaymentMethod paymentMethod,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String receivedAmount,
            @RequestParam(required = false, defaultValue = "false") Boolean requestInvoice,
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

        // Determinar si aplica Recargo de Equivalencia
        boolean applyRecargo = customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());

        // Procesar líneas de venta usando el sistema de precios temporales
        LocalDateTime now = LocalDateTime.now();
        List<SaleLine> lines = new ArrayList<>();
        List<TaxBreakdown> taxBreakdowns = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Product product = productService.findById(productIds.get(i));
            int qty = quantities.get(i);

            BigDecimal unitPrice;
            BigDecimal vatRate;
            ProductPrice activePrice = productPriceService.getCurrentPrice(product.getId(), now);
            if (activePrice != null) {
                unitPrice = activePrice.getPrice();
                vatRate = activePrice.getVatRate();
            } else {
                unitPrice = product.getPrice();
                vatRate = new BigDecimal("0.21"); // Default Spanish standard VAT
            }

            TaxBreakdown breakdown = recargoCalculator.calculateLineBreakdown(
                    product.getId(), product.getName(), unitPrice, qty, vatRate, applyRecargo);
            taxBreakdowns.add(breakdown);

            lines.add(SaleLine.builder()
                    .product(product)
                    .quantity(qty)
                    .unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
                    .vatRate(vatRate)
                    .build());
        }

        Worker worker = (Worker) session.getAttribute("worker");
        BigDecimal receivedAmountDecimal = null;
        if (paymentMethod == PaymentMethod.CASH && receivedAmount != null && !receivedAmount.isBlank()) {
            try {
                receivedAmountDecimal = new BigDecimal(receivedAmount.replace(",", "."));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        Sale sale = saleService.createSale(lines, paymentMethod, notes, receivedAmountDecimal, customer, worker);

        // Flash attributes for the receipt view
        redirectAttributes.addFlashAttribute("taxBreakdowns", taxBreakdowns);
        redirectAttributes.addFlashAttribute("applyRecargo", applyRecargo);

        BigDecimal totalBase = taxBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalVat = taxBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRecargo = taxBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        redirectAttributes.addFlashAttribute("totalBase", totalBase);
        redirectAttributes.addFlashAttribute("totalVat", totalVat);
        redirectAttributes.addFlashAttribute("totalRecargo", totalRecargo);

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
                // Generamos el reporte para asegurar que los datos son correctos,
                // aunque ya no se guarda el binario en la base de datos.
                // Pass the breakdown variables to the invoice report
                pdfReportService.generateInvoiceReport(sale, invoice, taxBreakdowns, applyRecargo, totalBase, totalVat,
                        totalRecargo);
            } else {
                // Para tickets: guardamos el flag de recargo en la venta y creamos el registro
                // de ticket correlativo
                saleService.saveApplyRecargo(sale.getId(), applyRecargo);
                ticketService.createTicket(sale, applyRecargo);

                // No generamos el PDF aquí para guardarlo, solo lo generamos si fuese necesario
                // para mostrarlo
                // (pero el flujo actual redirige al /receipt/{id} que lo regenerará para el
                // navegador)
            }

            if (invoice != null) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Factura " + invoice.getInvoiceNumber() + " generada.");
            }
        } catch (Exception e) {
            log.error("Error creating document record for sale " + sale.getId(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Venta completada pero hubo un error al generar el documento PDF.");
        }

        return "redirect:/tpv/receipt/" + sale.getId();
    }

    @GetMapping("/receipt/{saleId}")
    public String showReceipt(@PathVariable Long saleId, HttpSession session, Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        Sale sale = saleService.findById(saleId);
        model.addAttribute("sale", sale);

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

        java.math.BigDecimal totalWithdrawals = cashWithdrawalService.findByRegisterId(openRegister.getId()).stream()
                .map(CashWithdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        java.math.BigDecimal expectedCashInDrawer = openRegister.getOpeningBalance()
                .add(cashSalesToday)
                .subtract(totalWithdrawals)
                .subtract(cashRefundsToday);

        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("totalToday", saleService.sumTotalToday());
        model.addAttribute("countToday", saleService.countToday());
        model.addAttribute("todayRegister", openRegister);
        model.addAttribute("cashSalesToday", cashSalesToday);
        model.addAttribute("cashRefundsToday", cashRefundsToday);
        model.addAttribute("cardSalesToday", saleService.sumTotalByPaymentMethodToday(PaymentMethod.CARD));
        model.addAttribute("cardRefundsToday", returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CARD));
        model.addAttribute("totalWithdrawals", totalWithdrawals);
        model.addAttribute("expectedCashInDrawer", expectedCashInDrawer);
        return "tpv/cash-close";
    }

    @PostMapping("/withdrawal")
    public String processWithdrawal(
            @RequestParam String amount,
            @RequestParam(required = false) String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        if (!worker.getEffectivePermissions().contains("CASH_CLOSE")) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tiene permiso para realizar retiradas de caja.");
            return "redirect:/tpv";
        }

        try {
            java.util.Optional<CashRegister> openRegister = cashRegisterService.getOpenRegister();
            if (openRegister.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No hay ninguna caja abierta.");
                return "redirect:/tpv";
            }

            BigDecimal amountDecimal = new BigDecimal(amount.replace(",", "."));
            cashWithdrawalService.withdraw(openRegister.get().getId(), amountDecimal, reason, worker);

            redirectAttributes.addFlashAttribute("successMessage", "Retirada de "
                    + amountDecimal.setScale(2, RoundingMode.HALF_UP) + " \u20ac realizada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar la retirada: " + e.getMessage());
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
    public String showReturnReceipt(@PathVariable Long returnId, HttpSession session, Model model) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        if (!model.containsAttribute("saleReturn")) {
            SaleReturn saleReturn = returnService.findById(returnId)
                    .orElseThrow(() -> new IllegalArgumentException("Return not found: " + returnId));
            model.addAttribute("saleReturn", saleReturn);
        }
        return "tpv/return-receipt";
    }
}
