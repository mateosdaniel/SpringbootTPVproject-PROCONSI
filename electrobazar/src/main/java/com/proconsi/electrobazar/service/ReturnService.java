package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.dto.ReturnLineRequest;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.SaleReturn;
import com.proconsi.electrobazar.model.Worker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Interface defining operations for processing product returns and refunds.
 */
public interface ReturnService {

    Page<SaleReturn> findAll(Pageable pageable);

    /**
     * Retrieves returns with optional filtering (search query, payment method, and date).
     */
    Page<SaleReturn> getFilteredReturns(String search, String method, String date, Pageable pageable);

    /**
     * Processes a return for an original sale.
     * Validates quantities, restores stock, persists the record, and handles rectification.
     *
     * @param originalSaleId ID of the sale being returned.
     * @param lines          List of lines to return (saleLineId + quantity).
     * @param reason         Free-text reason for the return.
     * @param paymentMethod  How the refund will be issued (CASH or CARD).
     * @param worker         The worker processing the return.
     * @return The persisted SaleReturn entity.
     * @throws IllegalArgumentException if return quantities are invalid.
     */
    SaleReturn processReturn(Long originalSaleId, List<ReturnLineRequest> lines,
            String reason, PaymentMethod paymentMethod, Worker worker);

    /**
     * Finds a return by its ID.
     * @param id Primary key.
     * @return An Optional containing the SaleReturn.
     */
    Optional<SaleReturn> findById(Long id);

    /**
     * Retrieves all returns linked to a specific sale.
     * @param saleId The original sale ID.
     * @return A list of SaleReturn records.
     */
    List<SaleReturn> findByOriginalSaleId(Long saleId);

    /**
     * Aggregates total refunds processed today for a given payment method.
     * @param method CASH or CARD.
     * @return The total refunded BigDecimal.
     */
    BigDecimal sumTotalRefundedTodayByPaymentMethod(PaymentMethod method);

    /**
     * Aggregates total refunds within a specific date range and payment method.
     * @param from   Start timestamp.
     * @param to     End timestamp.
     * @param method CASH or CARD.
     * @return The total refunded amount.
     */
    BigDecimal sumTotalRefundedBetweenByPaymentMethod(LocalDateTime from, LocalDateTime to, PaymentMethod method);

    /**
     * Retrieves returns processed between two timestamps.
     * @param from Start date.
     * @param to   End date.
     * @return A list of matching returns.
     */
    List<SaleReturn> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
