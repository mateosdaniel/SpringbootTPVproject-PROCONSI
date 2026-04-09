package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.repository.CashRegisterRepository;
import com.proconsi.electrobazar.service.CashSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CashSessionServiceImpl implements CashSessionService {

    private final CashRegisterRepository cashRegisterRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CashRegister> getActiveSession() {
        return cashRegisterRepository.findFirstByClosedFalseOrderByRegisterDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashRegister> findAllClosed() {
        return cashRegisterRepository.findByClosedTrueOrderByRegisterDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegister findTodayIfClosed() {
        return cashRegisterRepository.findFirstByClosedTrueOrderByClosedAtDesc().orElse(null);
    }

    @Override
    public CashRegister openSession(BigDecimal initialCash, Worker worker) {
        if (getActiveSession().isPresent()) {
            throw new IllegalStateException("There is already an active session");
        }
        CashRegister session = CashRegister.builder()
                .openingBalance(initialCash)
                .worker(worker)
                .openingTime(LocalDateTime.now())
                .closed(false)
                .closingBalance(initialCash) // Initial expectation
                .build();
        return cashRegisterRepository.save(session);
    }

    @Override
    public CashRegister closeSession(BigDecimal actualCash, Worker worker) {
        CashRegister session = getActiveSession()
                .orElseThrow(() -> new IllegalStateException("No active session to close"));

        if (actualCash == null) {
            actualCash = BigDecimal.ZERO;
        }

        BigDecimal expectedCash = session.getClosingBalance() != null
                ? session.getClosingBalance()
                : BigDecimal.ZERO;

        session.setActualCash(actualCash);
        session.setClosingBalance(actualCash);
        session.setDifference(actualCash.subtract(expectedCash));
        session.setClosed(true);
        session.setClosedAt(LocalDateTime.now());
        session.setRetainedByWorker(worker);

        return cashRegisterRepository.save(session);
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegister findById(Long id) {
        return cashRegisterRepository.findById(id).orElse(null);
    }
}
