package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.model.Worker;
import java.math.BigDecimal;
import java.util.List;

/**
 * Interface defining operations for manual cash entries and withdrawals.
 * Tracks non-sale movements within a cash register shift.
 */
public interface CashWithdrawalService {

    /**
     * Records a manual cash movement (ENTRY or WITHDRAWAL).
     *
     * @param cashRegisterId The ID of the affected register.
     * @param amount         The monetary value.
     * @param reason         Description of why the cash was moved.
     * @param type           The movement type (ENTRY/WITHDRAWAL).
     * @param worker         The worker performing the operation.
     * @return The resulting CashWithdrawal entity.
     */
    CashWithdrawal processMovement(Long cashRegisterId, BigDecimal amount, String reason,
            CashWithdrawal.MovementType type, Worker worker);

    /**
     * Retrieves all manual movements for a specific register shift.
     *
     * @param registerId The shift ID.
     * @return A list of CashWithdrawal records.
     */
    List<CashWithdrawal> findByRegisterId(Long registerId);
}
