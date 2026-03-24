package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.dto.TaxBreakdown;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.TariffPriceHistoryRepository;
import com.proconsi.electrobazar.service.*;
import com.proconsi.electrobazar.util.RecargoEquivalenciaCalculator;
import com.proconsi.electrobazar.exception.InsufficientCashException;
import com.proconsi.electrobazar.dto.ReturnLineRequest;
import com.proconsi.electrobazar.dto.CashRegisterOpenSuggestion;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

/**
 * Controller for the Point of Sale (TPV) interface.
 * Manages sales processing, cash register operations, returns, and receipt
 * generation.
 */
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
    private final ProductPriceService productPriceService;
    private final RecargoEquivalenciaCalculator recargoCalculator;
    private final InvoiceService invoiceService;
    private final ReturnService returnService;
    private final TicketService ticketService;
    private final CashWithdrawalService cashWithdrawalService;
    private final ActivityLogService activityLogService;
    private final TariffService tariffService;
    private final TariffPriceHistoryRepository tariffPriceHistoryRepository;
    private final CompanySettingsService companySettingsService;

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
            @RequestParam(required = false) String cashAmount,
            @RequestParam(required = false) String cardAmount,
            @RequestParam(required = false, defaultValue = "false") Boolean requestInvoice,
            @RequestParam(required = false) Long tariffId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }

        // Process customer
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

        // Determine if Equivalency Surcharge applies
        boolean applyRecargo = customer != null && Boolean.TRUE.equals(customer.getHasRecargoEquivalencia());

        // Build sale lines using the unit prices sent by the frontend ticket.
        // These are already the final prices (tariff-adjusted by the sidebar) — do NOT
        // recalculate.
        List<SaleLine> lines = new ArrayList<>();

        for (int i = 0; i < productIds.size(); i++) {
            Product product = productService.findById(productIds.get(i));
            int qty = quantities.get(i);

            // Use the price sent from the ticket; fall back to productPriceService only if
            // missing.
            BigDecimal unitPrice;
            BigDecimal vatRate;

            if (unitPrices != null && i < unitPrices.size() && unitPrices.get(i) != null
                    && !unitPrices.get(i).isBlank()) {
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
        Sale sale = null;
        try {
            BigDecimal receivedAmountDecimal = null;
            if (paymentMethod == PaymentMethod.CASH && receivedAmount != null && !receivedAmount.isBlank()) {
                try {
                    receivedAmountDecimal = new BigDecimal(receivedAmount.replace(",", "."));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            BigDecimal cashAmountDecimal = null;
            if (cashAmount != null && !cashAmount.isBlank()) {
                try {
                    cashAmountDecimal = new BigDecimal(cashAmount.replace(",", "."));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            BigDecimal cardAmountDecimal = null;
            if (cardAmount != null && !cardAmount.isBlank()) {
                try {
                    cardAmountDecimal = new BigDecimal(cardAmount.replace(",", "."));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            sale = saleService.createSaleWithTariff(lines, paymentMethod, notes, receivedAmountDecimal, cashAmountDecimal, cardAmountDecimal, customer,
                    worker, tariffOverride);
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.error("Error creating sale: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/tpv";
        }

        // Generate and Store PDF in DB
        try {
            Invoice invoice = null;
            // Only create invoice if explicitly requested AND a customer is selected
            if (Boolean.TRUE.equals(requestInvoice) && customer != null) {
                invoice = invoiceService.createInvoice(sale);
                redirectAttributes.addFlashAttribute("invoice", invoice);
            }

            if (invoice != null) {
                // For invoices: update success message
                redirectAttributes.addFlashAttribute("successMessage",
                        "Invoice " + invoice.getInvoiceNumber() + " generated.");
            } else {
                // For tickets: create ticket record (the sale entity already has the correct applyRecargo flag)
                ticketService.createTicket(sale, applyRecargo);
            }
        } catch (Exception e) {
            log.error("Error creating document record for sale " + sale.getId(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Sale completed but there was an error generating the PDF document.");
        }

        return "redirect:/tpv/receipt/" + sale.getId();
    }

    /**
     * Displays the receipt for a completed sale.
     */
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
        model.addAttribute("companySettings", companySettingsService.getSettings());

        // Resolve invoice (from flash or DB)
        if (!model.containsAttribute("invoice")) {
            invoiceService.findBySaleId(saleId)
                    .ifPresent(inv -> model.addAttribute("invoice", inv));
        }

        // Resolve ticket (only if not an invoice)
        if (!model.containsAttribute("invoice") && !model.containsAttribute("ticket")) {
            ticketService.findBySaleId(saleId)
                    .ifPresent(t -> {
                        model.addAttribute("ticket", t);
                        model.addAttribute("qrCodeBase64", invoiceService.generateQrCodeBase64(t));
                    });
        }

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

        if (model.containsAttribute("invoice")) {
            Invoice invoice = (Invoice) model.asMap().get("invoice");
            model.addAttribute("qrCodeBase64", invoiceService.generateQrCodeBase64(invoice));
            return "tpv/invoice";
        }

        return "tpv/receipt";
    }

    /**
     * Displays the cash register closing form.
     */
    @GetMapping("/cash-close")
    public String cashCloseForm(HttpSession session, Model model) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        if (!worker.getEffectivePermissions().contains("CASH_CLOSE")) {
            return "redirect:/tpv";
        }

        Optional<CashRegister> openRegisterOpt = cashRegisterService.getOpenRegister();
        if (openRegisterOpt.isEmpty()) {
            return "redirect:/tpv/open-register";
        }

        CashRegister openRegister = openRegisterOpt.get();
        LocalDateTime startOfShift = openRegister.getOpeningTime() != null
                ? openRegister.getOpeningTime()
                : LocalDate.now().atStartOfDay();

        // 1. Fetch Summary (Combined query for Efficiency)
        SaleSummaryResponse summary = saleService.getSummaryToday();
        
        // 2. Fetch Payments & Movements
        BigDecimal cashSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CASH);
        BigDecimal cardSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CARD);
        BigDecimal cashRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CASH);
        BigDecimal cardRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CARD);

        List<CashWithdrawal> movements = cashWithdrawalService.findByRegisterId(openRegister.getId());
        BigDecimal totalWithdrawals = movements.stream()
                .filter(m -> m.getType() == null || m.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEntries = movements.stream()
                .filter(m -> m.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Expected Cash Logic
        BigDecimal expectedCashInDrawer = openRegister.getOpeningBalance()
                .add(cashSalesToday)
                .add(totalEntries)
                .subtract(totalWithdrawals)
                .subtract(cashRefundsToday);

        // 4. Model Population
        model.addAttribute("returnsToday", returnService.findByCreatedAtBetween(startOfShift, LocalDateTime.now()));
        model.addAttribute("cancelledCount", summary.getTotalCancelledCount());
        model.addAttribute("cancelledTotal", summary.getTotalCancelledAmount());
        model.addAttribute("totalToday", summary.getTotalSalesAmount());
        model.addAttribute("countToday", summary.getTotalSalesCount());
        
        model.addAttribute("todayRegister", openRegister);
        model.addAttribute("cashSalesToday", summary.getTotalCashAmount());
        model.addAttribute("cashRefundsToday", cashRefundsToday);
        model.addAttribute("cardSalesToday", summary.getTotalCardAmount());
        model.addAttribute("cardRefundsToday", cardRefundsToday);
        model.addAttribute("totalWithdrawals", totalWithdrawals);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("expectedCashInDrawer", expectedCashInDrawer);
        
        model.addAttribute("categories", categoryService.findAllActive());
        model.addAttribute("companySettings", companySettingsService.getSettings());

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
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You do not have permission to perform cash movements.");
            return "redirect:/tpv";
        }

        try {
            Optional<CashRegister> openRegister = cashRegisterService.getOpenRegister();
            if (openRegister.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "There is no open cash register.");
                return "redirect:/tpv";
            }

            BigDecimal amountDecimal = new BigDecimal(amount.replace(",", "."));
            CashWithdrawal.MovementType movementType = CashWithdrawal.MovementType.valueOf(type);

            cashWithdrawalService.processMovement(openRegister.get().getId(), amountDecimal, reason, movementType,
                    worker);

            String msg = (movementType == CashWithdrawal.MovementType.ENTRY ? "Entry" : "Withdrawal")
                    + " of " + amountDecimal.setScale(2, RoundingMode.HALF_UP) + " \u20ac performed successfully.";
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error processing movement: " + e.getMessage());
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
        CashRegisterOpenSuggestion suggestion = cashRegisterService.getOpenSuggestion();
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
        BigDecimal openingBalanceDecimal;
        try {
            openingBalanceDecimal = new BigDecimal(normalizedBalance);
        } catch (NumberFormatException e) {
            openingBalanceDecimal = BigDecimal.ZERO;
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
        BigDecimal closingBalanceDecimal = new BigDecimal(normalizedBalance);

        BigDecimal retainedAmountDecimal = null;
        if (retainedAmount != null && !retainedAmount.isBlank()) {
            try {
                retainedAmountDecimal = new BigDecimal(retainedAmount.replace(",", "."));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        CashRegister register = cashRegisterService.closeCashRegister(
                closingBalanceDecimal, notes, worker, retainedAmountDecimal);

        try {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Cash register closed. Difference: " + register.getDifference()
                            + "\u20AC. PDF stored in database.");
        } catch (Exception e) {
            log.error("Error generating/storing cash close PDF for register " + register.getId(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Closing performed but there was an error generating the PDF document.");
        }

        return "redirect:/tpv/open-register";
    }

    @GetMapping("/return/check")
    @ResponseBody
    public ResponseEntity<?> checkTicketForReturn(@RequestParam String query) {
        try {
            Long saleId = null;
            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                Sale sale = saleService.findById(id);
                if (sale != null) {
                    saleId = id;
                }
            }

            if (saleId == null) {
                saleId = ticketService.findByTicketNumber(query)
                        .map(t -> t.getSale().getId())
                        .orElse(null);
            }

            if (saleId != null) {
                return ResponseEntity
                        .ok(Collections.singletonMap("redirectUrl", "/tpv/return/" + saleId));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("errorMessage", "Ticket not found: " + query));
            }
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("errorMessage",
                            "Error searching for ticket: " + e.getMessage()));
        }
    }

    @GetMapping("/return/search")
    public String searchTicketForReturn(@RequestParam String query, RedirectAttributes redirectAttributes) {
        try {
            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                if (saleService.findById(id) != null) {
                    return "redirect:/tpv/return/" + id;
                }
            }

            return ticketService.findByTicketNumber(query)
                    .map(t -> "redirect:/tpv/return/" + t.getSale().getId())
                    .orElseGet(() -> {
                        redirectAttributes.addFlashAttribute("errorMessage", "Ticket not found: " + query);
                        return "redirect:/tpv";
                    });
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error searching for ticket: " + e.getMessage());
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

        Map<Long, Integer> alreadyReturned = new HashMap<>();
        for (SaleLine line : sale.getLines()) {
            int returned = returnService.findByOriginalSaleId(saleId).stream()
                    .flatMap(r -> r.getLines().stream())
                    .filter(rl -> rl.getSaleLine().getId().equals(line.getId()))
                    .mapToInt(ReturnLine::getQuantity)
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

        List<ReturnLineRequest> lineRequests = new ArrayList<>();
        for (int i = 0; i < saleLineIds.size(); i++) {
            lineRequests.add(new ReturnLineRequest(
                    saleLineIds.get(i), quantities.get(i)));
        }

        try {
            SaleReturn saleReturn = returnService.processReturn(
                    saleId, lineRequests, reason, paymentMethod, worker);
            redirectAttributes.addFlashAttribute("saleReturn", saleReturn);
            return "redirect:/tpv/return-receipt/" + saleReturn.getId();
        } catch (IllegalArgumentException | InsufficientCashException e) {
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
        model.addAttribute("companySettings", companySettingsService.getSettings());

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
            model.addAttribute("totalBase", negativeBreakdowns.stream().map(TaxBreakdown::getBaseAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalVat", negativeBreakdowns.stream().map(TaxBreakdown::getVatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            model.addAttribute("totalRecargo", negativeBreakdowns.stream().map(TaxBreakdown::getRecargoAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));

            List<Map<String, Object>> negativeLines = new ArrayList<>();
            for (ReturnLine line : saleReturn.getLines()) {
                if (line.getSaleLine() == null || line.getSaleLine().getProduct() == null)
                    continue;
                Map<String, Object> map = new HashMap<>();
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
            model.addAttribute("qrCodeBase64", invoiceService.generateQrCodeBase64(saleReturn.getRectificativeInvoice()));

            return "tpv/rectificative-invoice";
        }

        model.addAttribute("taxBreakdowns", standardBreakdowns);
        return "tpv/return-receipt";
    }

    /**
     * Returns the effective unit price for a product, optionally discounted by a
     * tariff.
     */
    @GetMapping("/api/products/{productId}/price")
    @ResponseBody
    public Map<String, BigDecimal> getProductEffectivePrice(
            @PathVariable Long productId,
            @RequestParam(required = false) Long tariffId) {

        Product product = productService.findById(productId);
        if (product == null) {
            return Collections.emptyMap();
        }

        Long effectiveTariffId = tariffId;
        if (effectiveTariffId == null) {
            effectiveTariffId = tariffService.findByName(Tariff.MINORISTA)
                    .map(Tariff::getId)
                    .orElse(null);
        }

        ProductPrice activePrice = productPriceService.getCurrentPrice(productId, LocalDateTime.now());
        BigDecimal basePrice = (activePrice != null) ? activePrice.getPrice() : product.getPrice();
        BigDecimal vatRate = (activePrice != null) ? activePrice.getVatRate()
                : (product.getTaxRate() != null && product.getTaxRate().getVatRate() != null
                        ? product.getTaxRate().getVatRate()
                        : new BigDecimal("0.21"));

        BigDecimal finalPrice = null;
        BigDecimal priceWithRe = null;

        if (effectiveTariffId != null) {
            var historyEntry = tariffPriceHistoryRepository.findCurrentByProductAndTariff(productId, effectiveTariffId);
            if (historyEntry.isPresent()) {
                finalPrice = historyEntry.get().getPriceWithVat();
                priceWithRe = historyEntry.get().getPriceWithRe();
            }
        }

        if (finalPrice == null) {
            if (effectiveTariffId != null) {
                finalPrice = tariffService.findById(effectiveTariffId)
                        .map(tariff -> {
                            BigDecimal discount = tariff.getDiscountPercentage();
                            if (discount == null || discount.compareTo(BigDecimal.ZERO) == 0) {
                                return basePrice;
                            }
                            BigDecimal factor = BigDecimal.ONE.subtract(
                                    discount.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                            return basePrice.multiply(factor);
                        })
                        .orElse(basePrice);
            } else {
                finalPrice = basePrice;
            }

            finalPrice = finalPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal reRate = recargoCalculator.getRecargoRate(vatRate);
            BigDecimal netPrice = finalPrice.divide(BigDecimal.ONE.add(vatRate), 10, RoundingMode.HALF_UP);
            priceWithRe = netPrice.multiply(BigDecimal.ONE.add(vatRate).add(reRate)).setScale(2, RoundingMode.HALF_UP);
        }

        Map<String, BigDecimal> response = new HashMap<>();
        response.put("price", finalPrice);
        response.put("priceWithRe", priceWithRe);
        return response;
    }
}
