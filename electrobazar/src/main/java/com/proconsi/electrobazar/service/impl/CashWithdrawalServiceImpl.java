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

@Service
@RequiredArgsConstructor
@Transactional
public class CashWithdrawalServiceImpl implements CashWithdrawalService {

    private final CashWithdrawalRepository cashWithdrawalRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final ActivityLogService activityLogService;

    @Override
    public CashWithdrawal withdraw(Long cashRegisterId, BigDecimal amount, String reason, Worker worker) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Importe inválido para la retirada");
        }

        CashRegister register = cashRegisterRepository.findById(cashRegisterId)
                .orElseThrow(() -> new ResourceNotFoundException("Caja no encontrada con id: " + cashRegisterId));

        if (register.getClosed()) {
            throw new IllegalStateException("No se puede realizar una retirada de una caja ya cerrada");
        }

        CashWithdrawal withdrawal = CashWithdrawal.builder()
                .cashRegister(register)
                .amount(amount)
                .reason(reason)
                .worker(worker)
                .build();

        CashWithdrawal saved = cashWithdrawalRepository.save(withdrawal);

        String username = worker != null ? worker.getUsername() : "Anónimo";
        activityLogService.logActivity(
                "RETIRADA_CAJA",
                "Retirada de caja de " + amount.setScale(2, java.math.RoundingMode.HALF_UP) + " \u20ac" +
                        (reason != null && !reason.isEmpty() ? ". Motivo: " + reason : ""),
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
