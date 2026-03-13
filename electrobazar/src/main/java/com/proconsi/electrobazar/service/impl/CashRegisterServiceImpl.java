package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.dto.DashboardStatsDTO;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.ProductRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.repository.SaleReturnRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Cierre de caja no encontrado con id: " + id));
        }

        @Override
        @Transactional(readOnly = true)
        public List<CashRegister> findAllClosed() {
                return cashRegisterRepository.findByClosedTrueOrderByRegisterDateDesc();
        }

        @Override
        @Transactional(readOnly = true)
        public CashRegister findTodayIfClosed() {
                return cashRegisterRepository.findByRegisterDateAndClosedTrue(LocalDate.now())
                                .orElseThrow(() -> new ResourceNotFoundException("No hay cierre de caja para hoy"));
        }

        @Override
        public CashRegister closeCashRegister(BigDecimal closingBalance, String notes,
                        com.proconsi.electrobazar.model.Worker worker,
                        BigDecimal retainedAmount) {
                try {
                        LocalDate today = LocalDate.now();

                        // Obtener registro abierto actual
                        CashRegister register = cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc()
                                        .orElse(CashRegister.builder()
                                                        .registerDate(today)
                                                        .openingBalance(BigDecimal.ZERO)
                                                        .build());

                        // Calcular totales solo desde la apertura de este registro
                        LocalDateTime startTime = register.getOpeningTime() != null ? register.getOpeningTime()
                                        : today.atStartOfDay();
                        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay().minusNanos(1);

                        BigDecimal cashSales = saleRepository
                                        .sumTotalBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CASH)
                                        .orElse(BigDecimal.ZERO);
                        BigDecimal cardSales = saleRepository
                                        .sumTotalBetweenByPaymentMethod(startTime, endOfDay, PaymentMethod.CARD)
                                        .orElse(BigDecimal.ZERO);
                        BigDecimal totalSales = cashSales.add(cardSales);

                        BigDecimal openingBal = register.getOpeningBalance() != null ? register.getOpeningBalance()
                                        : BigDecimal.ZERO;

                        // Separate withdrawals (OUT) from entries (IN)
                        BigDecimal totalWithdrawals = register.getWithdrawals().stream()
                                        .filter(w -> w.getType() == com.proconsi.electrobazar.model.CashWithdrawal.MovementType.WITHDRAWAL)
                                        .map(com.proconsi.electrobazar.model.CashWithdrawal::getAmount)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        BigDecimal totalEntries = register.getWithdrawals().stream()
                                        .filter(w -> w.getType() == com.proconsi.electrobazar.model.CashWithdrawal.MovementType.ENTRY)
                                        .map(com.proconsi.electrobazar.model.CashWithdrawal::getAmount)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        BigDecimal cashRefunds = saleReturnRepository
                                        .sumTotalRefundedBetweenByPaymentMethod(startTime, endOfDay,
                                                        PaymentMethod.CASH);
                        BigDecimal cardRefunds = saleReturnRepository
                                        .sumTotalRefundedBetweenByPaymentMethod(startTime, endOfDay,
                                                        PaymentMethod.CARD);

                        register.setCashSales(cashSales != null ? cashSales : BigDecimal.ZERO);
                        register.setCardSales(cardSales != null ? cardSales : BigDecimal.ZERO);
                        register.setTotalSales(totalSales != null ? totalSales : BigDecimal.ZERO);
                        register.setClosingBalance(closingBalance != null ? closingBalance : BigDecimal.ZERO);
                        register.setCashRefunds(cashRefunds != null ? cashRefunds : BigDecimal.ZERO);
                        register.setCardRefunds(cardRefunds != null ? cardRefunds : BigDecimal.ZERO);

                        // We use the subtracted sum of withdrawals and entries for the register's
                        // totalWithdrawals field if we want to keep it simple,
                        // but it's better to store just the net or the withdrawals only.
                        // For now, I'll store the net effect: Withdrawals - Entries? No, let's keep
                        // setTotalWithdrawals for actual withdrawals.
                        register.setTotalWithdrawals(totalWithdrawals != null ? totalWithdrawals : BigDecimal.ZERO);
                        register.setTotalEntries(totalEntries != null ? totalEntries : BigDecimal.ZERO);

                        BigDecimal expected = openingBal.add(register.getCashSales())
                                        .add(totalEntries)
                                        .subtract(totalWithdrawals)
                                        .subtract(register.getCashRefunds());
                        register.setDifference(register.getClosingBalance().subtract(expected));

                        register.setNotes(notes);
                        register.setClosedAt(LocalDateTime.now());
                        register.setClosed(true);
                        register.setWorker(worker);

                        // Persist retained cash for the next shift if provided
                        if (retainedAmount != null) {
                                register.setRetainedForNextShift(retainedAmount);
                                register.setRetainedByWorker(worker);
                        }

                        // Force save and flush to catch DB errors here
                        CashRegister closedRegister = cashRegisterRepository.saveAndFlush(register);

                        // Safety for logging
                        try {
                                String username = (worker != null) ? worker.getUsername() : "Anónimo";
                                BigDecimal diff = closedRegister.getDifference() != null
                                                ? closedRegister.getDifference()
                                                : BigDecimal.ZERO;
                                String difTxt = diff.setScale(2, java.math.RoundingMode.HALF_UP) + " \u20ac";

                                String retainedTxt = retainedAmount != null
                                                ? ". Retenido para siguiente turno: "
                                                                + retainedAmount.setScale(2,
                                                                                java.math.RoundingMode.HALF_UP)
                                                                + " \u20ac"
                                                : "";

                                activityLogService.logActivity(
                                                "CIERRE_CAJA",
                                                "Cierre de caja completado por " + username + " con descuadre de "
                                                                + difTxt + retainedTxt,
                                                username,
                                                "CASH_REGISTER",
                                                closedRegister.getId());
                        } catch (Exception logEx) {
                                System.err.println("Error creating activity log for cash close: " + logEx.getMessage());
                        }

                        return closedRegister;
                } catch (Exception e) {
                        System.err.println("CRITICAL ERROR IN closeCashRegister: " + e.getMessage());
                        e.printStackTrace();
                        throw e;
                }
        }

        @Override
        public java.util.Optional<CashRegister> getOpenRegister() {
                return cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc();
        }

        @Override
        public CashRegister openCashRegister(BigDecimal openingBalance, com.proconsi.electrobazar.model.Worker worker) {
                if (getOpenRegister().isPresent()) {
                        throw new IllegalStateException("Ya hay una caja abierta");
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
                String username = worker != null ? worker.getUsername() : "Anónimo";
                activityLogService.logActivity(
                                "APERTURA_CAJA",
                                "Apertura de caja realizada por " + username + " con saldo inicial de "
                                                + openingBalance.setScale(2, java.math.RoundingMode.HALF_UP)
                                                + " \u20ac",
                                username,
                                "CASH_REGISTER",
                                saved.getId());
                return saved;
        }

        @Override
        @Transactional(readOnly = true)
        public CashRegisterOpenSuggestion getOpenSuggestion() {
                return cashRegisterRepository.findFirstByClosedTrueOrderByClosedAtDesc()
                                .filter(r -> r.getRetainedForNextShift() != null)
                                .map(r -> CashRegisterOpenSuggestion.builder()
                                                .hasSuggestion(true)
                                                .suggestedBalance(r.getRetainedForNextShift())
                                                .build())
                                .orElse(CashRegisterOpenSuggestion.builder()
                                                .hasSuggestion(false)
                                                .suggestedBalance(null)
                                                .build());
        }

        @Override
        public BigDecimal calculateExpectedCashBalance(CashRegister register) {
                if (register == null) {
                        return BigDecimal.ZERO;
                }

                LocalDateTime startTime = register.getOpeningTime() != null ? register.getOpeningTime()
                                : register.getRegisterDate().atStartOfDay();
                LocalDateTime now = LocalDateTime.now();

                BigDecimal cashSales = saleRepository
                                .sumTotalBetweenByPaymentMethod(startTime, now, PaymentMethod.CASH)
                                .orElse(BigDecimal.ZERO);

                BigDecimal totalWithdrawals = register.getWithdrawals().stream()
                                .filter(w -> w.getType() == com.proconsi.electrobazar.model.CashWithdrawal.MovementType.WITHDRAWAL)
                                .map(com.proconsi.electrobazar.model.CashWithdrawal::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalEntries = register.getWithdrawals().stream()
                                .filter(w -> w.getType() == com.proconsi.electrobazar.model.CashWithdrawal.MovementType.ENTRY)
                                .map(com.proconsi.electrobazar.model.CashWithdrawal::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal cashRefunds = saleReturnRepository
                                .sumTotalRefundedBetweenByPaymentMethod(startTime, now, PaymentMethod.CASH);

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
        public DashboardStatsDTO getDashboardStats() {
                java.util.Optional<CashRegister> openRegister = getOpenRegister();
                LocalDateTime from;
                LocalDateTime to = LocalDateTime.now();
                boolean shiftActive = openRegister.isPresent();
                BigDecimal openingBalance = BigDecimal.ZERO;

                if (shiftActive) {
                        from = openRegister.get().getOpeningTime() != null ? openRegister.get().getOpeningTime()
                                        : LocalDate.now().atStartOfDay();
                        openingBalance = openRegister.get().getOpeningBalance();
                } else {
                        from = LocalDate.now().atStartOfDay();
                }

                BigDecimal revenue = saleRepository.sumTotalBetween(from, to);
                long salesCount = saleRepository.countByCreatedAtBetween(from, to);
                String topProduct = saleRepository.findTopProductNameBetween(from, to);
                long lowStockCount = productRepository.countByStockLessThan(5);

                return DashboardStatsDTO.builder()
                                .shiftActive(shiftActive)
                                .shiftOpeningTime(shiftActive ? openRegister.get().getOpeningTime() : null)
                                .revenue(revenue != null ? revenue : BigDecimal.ZERO)
                                .salesCount((int) salesCount)
                                .topProduct(topProduct != null ? topProduct : "—")
                                .lowStockCount((int) lowStockCount)
                                .openingBalance(openingBalance)
                                .build();
        }

}
