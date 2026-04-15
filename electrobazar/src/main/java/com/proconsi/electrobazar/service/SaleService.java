package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import com.proconsi.electrobazar.dto.AnalyticsSummaryDTO;
import com.proconsi.electrobazar.dto.WorkerSaleStatsDTO;
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
     * Retrieves a high-level analytics summary for a specific time range.
     * Use this instead of loading full Sale lists to optimize dashboard performance.
     */
    AnalyticsSummaryDTO getAnalyticsSummary(LocalDateTime from, LocalDateTime to);

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
     * Advanced search for sales in the admin panel.
     */
    Page<Sale> search(String search, String type, String method, java.time.LocalDate date, Pageable pageable);

    /**
     * Ultra-fast search for sales using Slice (no total count calculated).
     */
    org.springframework.data.domain.Slice<Sale> searchSlice(String search, String type, String method, java.time.LocalDate date, Pageable pageable);

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
     * Retrieves sales within a specific time range with pagination.
     * @param from Start timestamp.
     * @param to   End timestamp.
     * @param pageable Pagination and sorting criteria.
     * @return Page of sales in range.
     */
    Page<Sale> findBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * Retrieves sales within a specific time range for a specific worker with pagination.
     * @param from Start timestamp.
     * @param to   End timestamp.
     * @param workerId Worker ID to filter by.
     * @param pageable Pagination and sorting criteria.
     * @return Page of sales in range for the worker.
     */
    Page<Sale> findBetween(LocalDateTime from, LocalDateTime to, Long workerId, Pageable pageable);

    /**
     * Creates a standard sale for an anonymous customer.
     */
    Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Worker worker);

    /**
     * Creates a sale for a registered customer.
     */
    Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount, BigDecimal cashAmount, BigDecimal cardAmount, Customer customer, Worker worker);

    /**
     * Creates a sale with an explicit tariff override.
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
     */
    List<WorkerSaleStatsDTO> getWorkerStatsBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Cancels a sale and restores stock.
     */
    void cancelSale(Long id, Worker worker, String reason);
}