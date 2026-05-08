package com.proconsi.electrobazar.controller.web;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import com.proconsi.electrobazar.dto.CashRegisterOpenSuggestion;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Controller for cash register operations within the TPV.
 */
@Slf4j
@Controller
@RequestMapping("/tpv")
@RequiredArgsConstructor
public class TpvCashController {

    private final CashRegisterService cashRegisterService;
    private final SaleService saleService;
    private final ReturnService returnService;
    private final CashWithdrawalService cashWithdrawalService;
    private final CategoryService categoryService;
    private final CompanySettingsService companySettingsService;
    private final MessageSource messageSource;

    @GetMapping("/cash-close")
    public String cashCloseForm(HttpSession session, Model model) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        if (!worker.getEffectivePermissions().contains("CASH_CLOSE")) {
            return "redirect:/tpv";
        }

        Optional<CashRegister> activeRegisterOpt = cashRegisterService.getOpenRegister();
        if (activeRegisterOpt.isEmpty()) {
            return "redirect:/tpv/open-register";
        }

        CashRegister activeRegister = activeRegisterOpt.get();
        LocalDateTime startOfShift = activeRegister.getOpeningTime();

        SaleSummaryResponse summary = saleService.getSummaryToday();

        BigDecimal cashRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CASH);
        BigDecimal cardRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CARD);

        List<CashWithdrawal> movements = cashWithdrawalService.findByRegisterId(activeRegister.getId());
        BigDecimal totalWithdrawals = movements.stream()
                .filter(m -> m.getType() == null || m.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEntries = movements.stream()
                .filter(m -> m.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedCashInDrawer = activeRegister.getOpeningBalance()
                .add(summary.getTotalCashAmount())
                .subtract(cashRefundsToday)
                .add(totalEntries)
                .subtract(totalWithdrawals);

        model.addAttribute("returnsToday", returnService.findByCreatedAtBetween(startOfShift, LocalDateTime.now()));
        model.addAttribute("cancelledCount", summary.getTotalCancelledCount());
        model.addAttribute("cancelledTotal", summary.getTotalCancelledAmount());
        model.addAttribute("totalToday", summary.getTotalSalesAmount());
        model.addAttribute("countToday", summary.getTotalSalesCount());

        model.addAttribute("activeRegister", activeRegister);
        model.addAttribute("cashSalesToday", summary.getTotalCashAmount());
        model.addAttribute("cashRefundsToday", cashRefundsToday);
        model.addAttribute("cardSalesToday", summary.getTotalCardAmount());
        model.addAttribute("cardRefundsToday", cardRefundsToday);
        model.addAttribute("totalWithdrawals", totalWithdrawals);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("expectedCashInDrawer", expectedCashInDrawer);
        model.addAttribute("workerStats", saleService.getWorkerStatsBetween(startOfShift, LocalDateTime.now()));
        model.addAttribute("shiftStartTime", startOfShift.toString());
        model.addAttribute("todayRegister", activeRegister);

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
            Optional<CashRegister> activeRegisterOpt = cashRegisterService.getOpenRegister();
            if (activeRegisterOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "There is no active cash register.");
                return "redirect:/tpv";
            }
            CashRegister activeRegister = activeRegisterOpt.get();
            BigDecimal amountDecimal = new BigDecimal(amount.replace(",", "."));
            CashWithdrawal.MovementType movementType = CashWithdrawal.MovementType.valueOf(type.toUpperCase());

            cashWithdrawalService.processMovement(activeRegister.getId(), amountDecimal, reason, movementType,
                    worker);

            String msg = (movementType == CashWithdrawal.MovementType.ENTRY ? "Entry" : "Withdrawal")
                    + " of " + amountDecimal.setScale(2, RoundingMode.HALF_UP) + " € performed successfully.";
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (Exception e) {
            String localizedMsg = messageSource.getMessage("tpv.error.movement",
                    new Object[] { e.getMessage() }, LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("errorMessage", localizedMsg);
        }

        return "redirect:/tpv";
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
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null)
            return "redirect:/login";

        if (!worker.getEffectivePermissions().contains("CASH_CLOSE")) {
            return "redirect:/tpv";
        }

        String normalizedBalance = closingBalance.replace(",", ".");
        BigDecimal actualCash = new BigDecimal(normalizedBalance);

        CashRegister registerClosed = cashRegisterService.closeCashRegister(actualCash, null, worker, null);

        BigDecimal actual = registerClosed.getActualCash() != null
                ? registerClosed.getActualCash()
                : BigDecimal.ZERO;

        BigDecimal expected = registerClosed.getClosingBalance() != null
                ? registerClosed.getClosingBalance()
                : BigDecimal.ZERO;

        redirectAttributes.addFlashAttribute("successMessage",
                "Sesión cerrada correctamente. Diferencia: "
                        + actual.subtract(expected) + " €");

        return "redirect:/tpv";
    }
}
