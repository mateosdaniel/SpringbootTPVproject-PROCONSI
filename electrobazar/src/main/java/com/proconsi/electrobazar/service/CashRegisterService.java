package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.CashRegister;
import java.math.BigDecimal;
import java.util.List;

public interface CashRegisterService {
    CashRegister findById(Long id);

    List<CashRegister> findAllClosed();

    CashRegister findTodayIfClosed();

    CashRegister closeCashRegister(BigDecimal closingBalance, String notes,
            com.proconsi.electrobazar.model.Worker worker);

    java.util.Optional<CashRegister> getOpenRegister();

    CashRegister openCashRegister(BigDecimal openingBalance, com.proconsi.electrobazar.model.Worker worker);
}
