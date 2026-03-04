package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.ReturnLineRequest;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.SaleReturn;
import com.proconsi.electrobazar.model.Worker;

import java.util.List;
import java.util.Optional;

public interface ReturnService {

    /**
     * Processes a return for an original sale. Validates quantities, restores
     * stock,
     * persists the Return and its lines, and marks the related Invoice as RECTIFIED
     * if the return is total.
     *
     * @param originalSaleId ID of the sale being returned
     * @param lines          Lines to return (saleLineId + quantity per line)
     * @param reason         Free-text reason for the return
     * @param paymentMethod  How the refund will be issued (CASH or CARD)
     * @param worker         The worker processing the return
     * @return The persisted SaleReturn
     * @throws IllegalArgumentException if returned quantity exceeds available
     *                                  quantity
     */
    SaleReturn processReturn(Long originalSaleId, List<ReturnLineRequest> lines,
            String reason, PaymentMethod paymentMethod, Worker worker);

    /**
     * Finds a return by its ID.
     */
    Optional<SaleReturn> findById(Long id);

    /**
     * Finds all returns for a given original sale ID.
     */
    List<SaleReturn> findByOriginalSaleId(Long saleId);

    /**
     * Sums the total amount refunded today for a specific payment method.
     */
    java.math.BigDecimal sumTotalRefundedTodayByPaymentMethod(PaymentMethod method);

    /**
     * Sums the total amount refunded between two dates for a specific payment
     * method.
     */
    java.math.BigDecimal sumTotalRefundedBetweenByPaymentMethod(java.time.LocalDateTime from,
            java.time.LocalDateTime to,
            PaymentMethod method);
}
