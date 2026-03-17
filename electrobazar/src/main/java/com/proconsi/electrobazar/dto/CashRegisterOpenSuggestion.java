package com.proconsi.electrobazar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Returned by CashRegisterService.getOpenSuggestion() so the open-register
 * form can pre-fill the opening balance with the amount retained from the
 * last shift.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRegisterOpenSuggestion {

    /** True when the last closed register had a retainedForNextShift value. */
    private boolean hasSuggestion;

    /**
     * The suggested opening balance — equals the retained amount from the
     * previous shift. Returns {@code null} when hasSuggestion is false.
     */
    private BigDecimal suggestedBalance;
}
