package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface defining core operations for processing and auditing sales.
 */
public interface SaleService {

    /**
     * Finds a sale by ID.
     * @param id Primary key.
     * @return The Sale entity.
     */
    Sale findById(Long id);

    /**
     * Retrieves all sales records.
     * @return List of sales.
     */
    List<Sale> findAll();

    /**
     * Retrieves all sales records with pagination.
     * @param pageable Pagination and sorting criteria.
     * @return Page of sales.
     */
    Page<Sale> findAll(Pageable pageable);

    /**
     * Retrieves sales processed on the current day.
     * @return List of today's sales.
     */
    List<Sale> findToday();

    /**
     * Retrieves sales within a specific time range.
     * @param from Start timestamp.
     * @param to   End timestamp.
     * @return List of sales in range.
     */
    List<Sale> findBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Creates a standard sale for an anonymous customer.
     *
     * @param lines          Product lines.
     * @param paymentMethod  CASH or CARD.
     * @param notes          Optional notes.
     * @param receivedAmount Cash provided by the customer.
     * @param worker         The worker processing the sale.
     * @return The persisted Sale.
     */
    Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Worker worker);

    /**
     * Creates a sale for a registered customer.
     *
     * @param lines          Product lines.
     * @param paymentMethod  CASH or CARD.
     * @param notes          Optional notes.
     * @param receivedAmount Cash provided.
     * @param customer       Associated customer.
     * @param worker         The worker.
     * @return The persisted Sale.
     */
    Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer, Worker worker);

    /**
     * Creates a sale with an explicit tariff override.
     *
     * @param lines          Product lines.
     * @param paymentMethod  CASH or CARD.
     * @param notes          Optional notes.
     * @param receivedAmount Cash provided.
     * @param customer       Associated customer.
     * @param worker         The worker.
     * @param tariffOverride Specific tariff to apply (overrides customer default).
     * @return The persisted Sale.
     */
    Sale createSaleWithTariff(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer,
            Worker worker, Tariff tariffOverride);

    /**
     * Creates a sale with an explicit tariff override and a discount coupon.
     */
    Sale createSaleWithCoupon(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
            BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer,
            Worker worker, Tariff tariffOverride, String couponCode);

    /**
     * Aggregates the total revenue from today's sales.
     * @return Total BigDecimal amount.
     */
    BigDecimal sumTotalToday();

    /**
     * Counts the number of transactions processed today.
     * @return Current day count.
     */
    long countToday();

    /**
     * Aggregates revenue for a specific payment method today.
     * @param paymentMethod CASH or CARD.
     * @return Subtotal for that method.
     */
    BigDecimal sumTotalByPaymentMethodToday(PaymentMethod paymentMethod);

    /**
     * Generates a high-level summary of today's commercial activity.
     * @return A DTO with today's stats.
     */
    SaleSummaryResponse getSummaryToday();

    /**
     * Retrieves sales statistics aggregated by worker for a given period.
     * @param from Start timestamp.
     * @param to   End timestamp.
     * @return List of worker statistics.
     */
    java.util.List<com.proconsi.electrobazar.dto.WorkerSaleStatsDTO> getWorkerStatsBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    void cancelSale(Long id, Worker worker, String reason);
}