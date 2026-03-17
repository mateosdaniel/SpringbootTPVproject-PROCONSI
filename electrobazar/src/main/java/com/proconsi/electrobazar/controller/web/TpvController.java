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
 * Manages sales processing, cash register operations, returns, and receipt generation.
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
        // Validate cash payment limit (Law 11/2021)
        if (paymentMethod == PaymentMethod.CASH && maxPosibleAmount.compareTo(new BigDecimal("1000")) >= 0) {
            activityLogService.logActivity("CASH_LIMIT_VIOLATION",
                    "Cash payment attempt blocked for amount >= 1000€ (Estimated total: " + maxPosibleAmount
                            + "€)",
                    worker.getUsername(), "SALE", null);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cash payment is not permitted for amounts equal to or greater than 1,000 € according to Law 11/2021 on fiscal fraud prevention. Please select another payment method.");
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
                // For tickets: save surcharge flag and create ticket record
                saleService.saveApplyRecargo(sale.getId(), applyRecargo);
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
     *
     * @param saleId The ID of the sale to display the receipt for.
     * @param autoPrint Flag to indicate if the receipt should be automatically printed.
     * @param session The current HTTP session to retrieve worker information.
     * @param model The model to pass data to the view.
     * @return The receipt view (ticket or invoice).
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

    /**
     * Displays the cash register closing form, including summary of daily cash movements.
     *
     * @param session The current HTTP session to retrieve worker information.
     * @param model The model to pass data to the view.
     * @return The cash close form view or a redirect if no register is open or permissions are insufficient.
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
        BigDecimal cashSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CASH);
        BigDecimal cashRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CASH);

        List<CashWithdrawal> movements = cashWithdrawalService.findByRegisterId(openRegister.getId());
        BigDecimal totalWithdrawals = movements.stream()
                .filter(m -> m.getType() == null || m.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEntries = movements.stream()
                .filter(m -> m.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startOfShift = openRegister.getOpeningTime() != null
                ? openRegister.getOpeningTime()
                : LocalDate.now().atStartOfDay();
        model.addAttribute("returnsToday",
                returnService.findByCreatedAtBetween(startOfShift, LocalDateTime.now()));

        BigDecimal expectedCashInDrawer = openRegister.getOpeningBalance()
                .add(cashSalesToday)
                .add(totalEntries)
                .subtract(totalWithdrawals)
                .subtract(cashRefundsToday);

        SaleSummaryResponse summary = saleService.getSummaryToday();
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

    /**
     * Processes a cash withdrawal or entry movement for the open cash register.
     *
     * @param amount The amount of the movement.
     * @param reason Optional reason for the movement.
     * @param type The type of movement (WITHDRAWAL or ENTRY).
     * @param session The current HTTP session to retrieve worker information.
     * @param redirectAttributes Attributes for redirecting with messages.
     * @return A redirect to the TPV main page with a success or error message.
     */
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
            redirectAttributes.addFlashAttribute("errorMessage", "You do not have permission to perform cash movements.");
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

    /**
     * Displays the TPV preferences page.
     *
     * @param session The current HTTP session to retrieve worker information.
     * @return The preferences view or a redirect to login.
     */
    @GetMapping("/preferences")
    public String preferences(HttpSession session) {
        if (session.getAttribute("worker") == null) {
            return "redirect:/login";
        }
        return "tpv/preferences";
    }

    /**
     * Displays the form to open a new cash register.
     *
     * @param session The current HTTP session to retrieve worker information.
     * @param model The model to pass data to the view.
     * @return The open-register form view or a redirect to the TPV main page if a register is already open.
     */
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

    /**
     * Processes the opening of a new cash register.
     *
     * @param openingBalance The initial balance for the cash register.
     * @param session The current HTTP session to retrieve worker information.
     * @return A redirect to the TPV main page.
     */
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

    /**
     * Processes the closing of the current cash register.
     *
     * @param closingBalance The final balance counted in the cash register.
     * @param notes Optional notes for the closing.
     * @param retainedAmount Optional amount to be retained in the cash register for the next opening.
     * @param session The current HTTP session to retrieve worker information.
     * @param redirectAttributes Attributes for redirecting with messages.
     * @return A redirect to the open-register form with a success or error message.
     */
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
                // Ignore malformed values — treat as no retention
            }
        }

        CashRegister register = cashRegisterService.closeCashRegister(
                closingBalanceDecimal, notes, worker, retainedAmountDecimal);

        try {
            // We no longer generate or save the PDF here. It's regenerated on download.

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

    /**
     * Checks for a ticket by ID or ticket number for a return operation.
     *
     * @param query The ticket ID or ticket number to search for.
     * @return A ResponseEntity with a redirect URL if found, or an error message if not.
     */
    @GetMapping("/return/check")
    @ResponseBody
    public ResponseEntity<?> checkTicketForReturn(@RequestParam String query) {
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

    /**
     * Searches for a ticket by ID or ticket number and redirects to the return form if found.
     *
     * @param query The ticket ID or ticket number to search for.
     * @param redirectAttributes Attributes for redirecting with messages.
     * @return A redirect to the return form or the TPV main page with an error message.
     */
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
                        redirectAttributes.addFlashAttribute("errorMessage", "Ticket not found: " + query);
                        return "redirect:/tpv";
                    });
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error searching for ticket: " + e.getMessage());
            return "redirect:/tpv";
        }
    }

    /**
     * Displays the return form for a specific sale.
     *
     * @param saleId The ID of the original sale for which to process a return.
     * @param session The current HTTP session to retrieve worker information.
     * @param model The model to pass data to the view.
     * @return The return form view or a redirect to login.
     */
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

    /**
     * Processes a return for a sale.
     *
     * @param saleId The ID of the original sale.
     * @param saleLineIds List of IDs of the sale lines to return.
     * @param quantities List of quantities to return for each sale line.
     * @param reason Optional reason for the return.
     * @param paymentMethod The payment method used for the refund.
     * @param session The current HTTP session to retrieve worker information.
     * @param redirectAttributes Attributes for redirecting with messages.
     * @return A redirect to the return receipt or the return form with an error message.
     */
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

    /**
     * Displays the receipt for a processed return.
     *
     * @param returnId The ID of the return to display the receipt for.
     * @param autoPrint Flag to indicate if the receipt should be automatically printed.
     * @param session The current HTTP session to retrieve worker information.
     * @param model The model to pass data to the view.
     * @return The return receipt view (standard or rectificative invoice).
     */
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
    public Map<String, BigDecimal> getProductEffectivePrice(
            @PathVariable Long productId,
            @RequestParam(required = false) Long tariffId) {

        Product product = productService.findById(productId);
        if (product == null) {
            return Collections.emptyMap();
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

        Map<String, BigDecimal> response = new HashMap<>();
        response.put("price", finalPrice);
        response.put("priceWithRe", priceWithRe);
        return response;
    }
}
