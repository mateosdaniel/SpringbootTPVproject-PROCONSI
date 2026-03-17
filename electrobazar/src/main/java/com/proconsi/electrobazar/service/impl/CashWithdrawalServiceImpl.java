package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.repository.CashWithdrawalRepository;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CashWithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation of {@link CashWithdrawalService}.
 * Handles manual cash entries and withdrawals within an open shift.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CashWithdrawalServiceImpl implements CashWithdrawalService {

    private final CashWithdrawalRepository cashWithdrawalRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final ActivityLogService activityLogService;

    @Override
    public CashWithdrawal processMovement(Long cashRegisterId, BigDecimal amount, String reason,
            CashWithdrawal.MovementType type, Worker worker) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        CashRegister register = cashRegisterRepository.findById(cashRegisterId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash register not found with id: " + cashRegisterId));

        if (register.getClosed()) {
            throw new IllegalStateException("Cannot perform movements on a closed shift.");
        }

        CashWithdrawal movement = CashWithdrawal.builder()
                .cashRegister(register)
                .amount(amount)
                .reason(reason)
                .worker(worker)
                .type(type)
                .build();

        CashWithdrawal saved = cashWithdrawalRepository.save(movement);

        String typeLabel = type == CashWithdrawal.MovementType.ENTRY ? "ENTRY" : "WITHDRAWAL";
        String username = worker != null ? worker.getUsername() : "Anonymous";

        activityLogService.logActivity(
                type == CashWithdrawal.MovementType.ENTRY ? "ENTRADA_CAJA" : "RETIRADA_CAJA",
                String.format("%s of %.2f €. Reason: %s", typeLabel, amount, (reason != null ? reason : "N/A")),
                username,
                "CASH_REGISTER",
                register.getId());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashWithdrawal> findByRegisterId(Long registerId) {
        return cashWithdrawalRepository.findByCashRegisterId(registerId);
    }
}


