package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.CashRegisterOpenSuggestion;
import com.proconsi.electrobazar.model.CashRegister;
import java.math.BigDecimal;
import java.util.List;

public interface CashRegisterService {
    CashRegister findById(Long id);

    List<CashRegister> findAllClosed();

    CashRegister findTodayIfClosed();

    /**
     * Closes the open cash register.
     *
     * @param closingBalance total cash counted in the drawer.
     * @param notes          optional freetext observation.
     * @param worker         the worker performing the close.
     * @param retainedAmount optional amount to keep in the drawer for the next
     *                       shift. When non-null, stored as
     *                       {@code retainedForNextShift} on the register and
     *                       used as a suggested opening balance for the next
     *                       {@link #getOpenSuggestion()} call.
     */
    CashRegister closeCashRegister(BigDecimal closingBalance, String notes,
            com.proconsi.electrobazar.model.Worker worker,
            BigDecimal retainedAmount);

    java.util.Optional<CashRegister> getOpenRegister();

    CashRegister openCashRegister(BigDecimal openingBalance, com.proconsi.electrobazar.model.Worker worker);

    /**
     * Looks at the most recently closed register. If it had a
     * {@code retainedForNextShift} value, returns a suggestion carrying that
     * amount so the open-register form can pre-fill the balance input.
     */
    CashRegisterOpenSuggestion getOpenSuggestion();
}
