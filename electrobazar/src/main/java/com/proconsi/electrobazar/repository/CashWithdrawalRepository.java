package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.CashWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link CashWithdrawal} entities.
 * Tracks manual cash entries and withdrawals associated with specific terminal shifts.
 */
@Repository
public interface CashWithdrawalRepository extends JpaRepository<CashWithdrawal, Long> {

    /**
     * Retrieves all cash movements for a given shift.
     * @param registerId ID of the cash register shift.
     * @return List of movements.
     */
    List<CashWithdrawal> findByCashRegisterId(Long registerId);
}
