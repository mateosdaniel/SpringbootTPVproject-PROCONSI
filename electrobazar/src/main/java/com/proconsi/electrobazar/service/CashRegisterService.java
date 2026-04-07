package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.CashRegisterOpenSuggestion;
import com.proconsi.electrobazar.dto.DashboardStatsDTO;
import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.Worker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Interface defining operations for managing the shop's cash register shifts.
 * Includes opening, closing, and auditing financial movements.
 */
public interface CashRegisterService {

    /**
     * Finds a specific cash register by its ID.
     * @param id The primary key.
     * @return The found CashRegister.
     */
    CashRegister findById(Long id);

    /**
     * Retrieves all closed cash registers for historical analysis.
     * @return A list of closed CashRegister entities.
     */
    List<CashRegister> findAllClosed();

    /**
     * Paginated version of closed registers retrieval.
     */
    org.springframework.data.domain.Page<CashRegister> findAllClosed(org.springframework.data.domain.Pageable pageable);

    /**
     * Filters cash registers by worker and date with pagination/sorting.
     */
    org.springframework.data.domain.Page<CashRegister> getFilteredRegisters(String worker, String date, org.springframework.data.domain.Pageable pageable);

    /**
     * Finds the register for today if it is already closed.
     * @return The CashRegister if closed, null otherwise.
     */
    CashRegister findTodayIfClosed();

    /**
     * Closes the currently open cash register.
     *
     * @param closingBalance Total cash counted in the drawer.
     * @param notes          Optional observations.
     * @param worker         The worker performing the close operation.
     * @param retainedAmount Amount to keep in the drawer for the next shift.
     * @return The updated CashRegister.
     */
    CashRegister closeCashRegister(BigDecimal closingBalance, String notes,
            Worker worker,
            BigDecimal retainedAmount);

    /**
     * Retrieves the currently open cash register, if any.
     * @return An Optional containing the active CashRegister.
     */
    Optional<CashRegister> getOpenRegister();

    /**
     * Opens a new cash register shift.
     *
     * @param openingBalance Starting cash amount in the drawer.
     * @param worker         The worker opening the shift.
     * @return The newly created CashRegister.
     */
    CashRegister openCashRegister(BigDecimal openingBalance, Worker worker);

    /**
     * Suggests an opening balance based on the last shift's retained amount.
     * @return A DTO with the suggested opening values.
     */
    CashRegisterOpenSuggestion getOpenSuggestion();

    /**
     * Calculates the theoretical cash balance that should be in the drawer.
     * @param register The register to audit.
     * @return The expected BigDecimal total.
     */
    BigDecimal calculateExpectedCashBalance(CashRegister register);

    /**
     * Convenience method to get the expected balance for the current shift.
     * @return The current expected cash total.
     */
    BigDecimal getCurrentCashBalance();

    /**
     * Verifies that a cash register session is open for the current day.
     * Throws IllegalStateException if no open register exists for today.
     */
    void checkOpenRegisterForToday();

    /**
     * Aggregates financial statistics for dashboard visualizations.
     * @param period The time range (e.g., "today", "week").
     * @return A DTO with aggregated statistics.
     */
    DashboardStatsDTO getDashboardStats(String period);
}


