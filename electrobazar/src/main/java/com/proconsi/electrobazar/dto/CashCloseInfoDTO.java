package com.proconsi.electrobazar.dto;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.SaleReturn;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO containing summary information for a cash register closing operation.
 * Used to transfer daily statistics and totals to the view or API responses.
 */
@Data
@Builder
public class CashCloseInfoDTO {
    /** Total sales amount for the current day. */
    private BigDecimal totalToday;
    
    /** Total number of sales performed today. */
    private long countToday;
    
    /** Total amount of sales paid by card. */
    private BigDecimal cardSalesToday;
    
    /** Total amount of refunds processed for card payments today. */
    private BigDecimal cardRefundsToday;
    
    /** Total amount of sales paid in cash. */
    private BigDecimal cashSalesToday;
    
    /** Total amount of refunds processed for cash payments today. */
    private BigDecimal cashRefundsToday;
    
    /** Total amount of cash entries (injections) today. */
    private BigDecimal totalEntries;
    
    /** Total amount of cash withdrawals today. */
    private BigDecimal totalWithdrawals;
    
    /** Theoretical amount of cash that should be in the drawer. */
    private BigDecimal expectedCashInDrawer;
    
    /** Number of cancelled sales today. */
    private long cancelledCount;
    
    /** Total amount of cancelled sales today. */
    private BigDecimal cancelledTotal;
    
    /** List of returns processed today. */
    private List<SaleReturn> returnsToday;
    
    /** Balance in the register at the start of the shift. */
    private BigDecimal openingBalance;
    
    /** The actual CashRegister entity record. */
    private CashRegister todayRegister;
    
    /** Per-worker breakdown of sales for the shift. */
    @Builder.Default
    private List<WorkerSaleStatsDTO> workerStats = new ArrayList<>();
}
