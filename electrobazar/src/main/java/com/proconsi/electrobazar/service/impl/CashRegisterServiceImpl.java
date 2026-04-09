package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.CashRegisterOpenSuggestion;
import com.proconsi.electrobazar.dto.DashboardStatsDTO;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.repository.SaleReturnRepository;
import com.proconsi.electrobazar.repository.specification.CashRegisterSpecification;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link CashRegisterService}.
 * Manages the lifecycle of cash register shifts, including opening balances,
 * sales tracking, and closing reconciliations with calculated differences.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CashRegisterServiceImpl implements CashRegisterService {

    private final CashRegisterRepository cashRegisterRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final ProductRepository productRepository;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional(readOnly = true)
    public CashRegister findById(Long id) {
        return cashRegisterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash register not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashRegister> findAllClosed() {
        return cashRegisterRepository.findByClosedTrueOrderByRegisterDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CashRegister> findAllClosed(Pageable pageable) {
        return cashRegisterRepository.findByClosedTrue(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CashRegister> getFilteredRegisters(String worker, String date, Pageable pageable) {
        Specification<CashRegister> spec = CashRegisterSpecification.filterRegisters(worker, date);
        return cashRegisterRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegister findTodayIfClosed() {
        return cashRegisterRepository.findByRegisterDateAndClosedTrue(LocalDate.now())
                .orElseThrow(() -> new ResourceNotFoundException("No cash register closure found for today."));
    }

    @Override
    public CashRegister closeCashRegister(BigDecimal closingBalance, String notes, Worker worker, BigDecimal retainedAmount) {
        LocalDate today = LocalDate.now();

        // Retrieve current open register or create a dummy one if none found (should not happen in normal flow)
        CashRegister register = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .orElse(CashRegister.builder()
                        .registerDate(today)
                        .openingBalance(BigDecimal.ZERO)
                        .build());

        LocalDateTime startTime = register.getOpeningTime() != null ? register.getOpeningTime() : today.atStartOfDay();
        LocalDateTime endOfDay = LocalDateTime.now();

        // Aggregate totals for the current shift
        BigDecimal cashSales = saleRepository.sumTotalBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CASH)
                .orElse(BigDecimal.ZERO);
        BigDecimal cardSales = saleRepository.sumTotalBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CARD)
                .orElse(BigDecimal.ZERO);
        BigDecimal totalSales = cashSales.add(cardSales);

        BigDecimal openingBal = register.getOpeningBalance() != null ? register.getOpeningBalance() : BigDecimal.ZERO;

        BigDecimal totalWithdrawals = register.getWithdrawals().stream()
                .filter(w -> w.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(CashWithdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEntries = register.getWithdrawals().stream()
                .filter(w -> w.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(CashWithdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashRefunds = saleReturnRepository.sumTotalRefundedBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CASH);
        BigDecimal cardRefunds = saleReturnRepository.sumTotalRefundedBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CARD);

        // Map values to entity
        register.setCashSales(cashSales);
        register.setCardSales(cardSales);
        register.setTotalSales(totalSales);
        register.setClosingBalance(closingBalance != null ? closingBalance : BigDecimal.ZERO);
        register.setActualCash(register.getClosingBalance());
        register.setCashRefunds(cashRefunds);
        register.setCardRefunds(cardRefunds);
        register.setTotalWithdrawals(totalWithdrawals);
        register.setTotalEntries(totalEntries);

        // Calculate discrepancy
        BigDecimal expected = openingBal.add(register.getCashSales())
                .add(totalEntries)
                .subtract(totalWithdrawals)
                .subtract(register.getCashRefunds());
        register.setDifference(register.getClosingBalance().subtract(expected));

        register.setNotes(notes);
        register.setClosedAt(LocalDateTime.now());
        register.setClosed(true);
        register.setWorker(worker);

        if (retainedAmount != null) {
            register.setRetainedForNextShift(retainedAmount);
            register.setRetainedByWorker(worker);
        }

        CashRegister saved = cashRegisterRepository.saveAndFlush(register);

        // Audit Trail
        String username = (worker != null) ? worker.getUsername() : "Anonymous";
        BigDecimal diff = saved.getDifference() != null ? saved.getDifference() : BigDecimal.ZERO;
        String logMsg = String.format("Turno cerrado por %s. Diferencial: %.2f €", username, diff);
        if (retainedAmount != null) {
            logMsg += String.format(". Retenido para el próximo turno: %.2f €", retainedAmount);
        }

        activityLogService.logActivity("CIERRE_CAJA", logMsg, username, "CASH_REGISTER", saved.getId());
        activityLogService.logFiscalEvent("SHIFT_CLOSE", 
                String.format("Cierre de sesión fiscal ID %d. Saldo final: %.2f€. Diferencia: %.2f€.", 
                saved.getId(), saved.getClosingBalance(), diff), username);

        return saved;
    }

    @Override
    public void checkOpenRegisterForToday() {
        boolean isRegisterOpen = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                .isPresent();

        if (!isRegisterOpen) {
            throw new IllegalStateException("No hay ninguna sesión de caja abierta. Abra la caja antes de realizar ventas.");
        }
    }

    @Override
    public Optional<CashRegister> getOpenRegister() {
        return cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc();
    }

    @Override
    public CashRegister openCashRegister(BigDecimal openingBalance, Worker worker) {
        if (getOpenRegister().isPresent()) {
            throw new IllegalStateException("An open shift already exists.");
        }

        CashRegister newRegister = CashRegister.builder()
                .registerDate(LocalDate.now())
                .openingBalance(openingBalance)
                .openingTime(LocalDateTime.now())
                .cashSales(BigDecimal.ZERO)
                .cardSales(BigDecimal.ZERO)
                .totalSales(BigDecimal.ZERO)
                .closed(false)
                .worker(worker)
                .build();

        CashRegister saved = cashRegisterRepository.save(newRegister);
        String username = worker != null ? worker.getUsername() : "Anonymous";

        activityLogService.logActivity(
                "APERTURA_CAJA",
                String.format("Nuevo turno abierto por %s con %.2f €", username, openingBalance),
                username,
                "CASH_REGISTER",
                saved.getId());
        activityLogService.logFiscalEvent("SHIFT_OPEN", 
                String.format("Apertura de sesión fiscal ID %d con saldo inicial de %.2f€.", 
                saved.getId(), openingBalance), username);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterOpenSuggestion getOpenSuggestion() {
        return cashRegisterRepository.findFirstByClosedTrueOrderByClosedAtDesc()
                .map(r -> {
                    BigDecimal suggested = r.getRetainedForNextShift() != null ? 
                        r.getRetainedForNextShift() : r.getClosingBalance();
                    return CashRegisterOpenSuggestion.builder()
                            .hasSuggestion(true)
                            .suggestedBalance(suggested != null ? suggested : BigDecimal.ZERO)
                            .build();
                })
                .orElse(CashRegisterOpenSuggestion.builder()
                        .hasSuggestion(false)
                        .build());
    }

    @Override
    public BigDecimal calculateExpectedCashBalance(CashRegister register) {
        if (register == null) return BigDecimal.ZERO;

        LocalDateTime startTime = register.getOpeningTime() != null ? register.getOpeningTime() : register.getRegisterDate().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        BigDecimal cashSales = saleRepository.sumTotalBetweenByPaymentMethod(startTime, now, PaymentMethod.CASH)
                .orElse(BigDecimal.ZERO);

        BigDecimal totalWithdrawals = register.getWithdrawals().stream()
                .filter(w -> w.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(CashWithdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEntries = register.getWithdrawals().stream()
                .filter(w -> w.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(CashWithdrawal::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashRefunds = saleReturnRepository.sumTotalRefundedBetweenByPaymentMethod(startTime, now, PaymentMethod.CASH);

        return register.getOpeningBalance()
                .add(cashSales)
                .add(totalEntries)
                .subtract(totalWithdrawals)
                .subtract(cashRefunds);
    }

    @Override
    public BigDecimal getCurrentCashBalance() {
        return getOpenRegister()
                .map(this::calculateExpectedCashBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats(String period) {
        Optional<CashRegister> openRegister = getOpenRegister();
        LocalDateTime from;
        LocalDateTime to = LocalDateTime.now();
        boolean shiftActive = openRegister.isPresent();
        BigDecimal openingBalance = shiftActive ? openRegister.get().getOpeningBalance() : BigDecimal.ZERO;

        if (period == null || period.equalsIgnoreCase("shift")) {
            from = shiftActive ? openRegister.get().getOpeningTime() : LocalDate.now().atStartOfDay();
        } else {
            from = switch (period.toLowerCase()) {
                case "today" -> LocalDate.now().atStartOfDay();
                case "7days" -> LocalDateTime.now().minusDays(7);
                case "1month" -> LocalDateTime.now().minusMonths(1);
                case "6months" -> LocalDateTime.now().minusMonths(6);
                case "1year" -> LocalDateTime.now().minusYears(1);
                case "all" -> LocalDateTime.of(2000, 1, 1, 0, 0);
                default -> LocalDate.now().atStartOfDay();
            };
        }

        // ONE DB pass for all sales KPIs — now using an Interface Projection to avoid Hibernate class instantiation issues
        com.proconsi.electrobazar.dto.SaleSummaryProjection summary = saleRepository.getSummaryBetween(from, to);
        String topProduct = saleRepository.findTopProductNameBetween(from, to);
        // O(1) in DB vs O(n) in Java
        long lowStockCount = productRepository.countByStockLessThan(new BigDecimal("5"));

        return DashboardStatsDTO.builder()
                .shiftActive(shiftActive)
                .shiftOpeningTime(shiftActive ? openRegister.get().getOpeningTime() : null)
                .revenue(summary.getTotalSalesAmount() != null ? summary.getTotalSalesAmount() : BigDecimal.ZERO)
                .salesCount((int) (summary.getTotalSalesCount() != null ? summary.getTotalSalesCount() : 0))
                .topProduct(topProduct != null ? topProduct : "—")
                .lowStockCount((int) lowStockCount)
                .openingBalance(openingBalance)
                .build();

    }
}
