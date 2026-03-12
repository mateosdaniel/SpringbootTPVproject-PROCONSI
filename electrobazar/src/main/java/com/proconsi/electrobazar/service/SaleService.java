package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;
import com.proconsi.electrobazar.model.Tariff;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleService {
        Sale findById(Long id);

        List<Sale> findAll();

        List<Sale> findToday();

        List<Sale> findBetween(LocalDateTime from, LocalDateTime to);

        Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
                        com.proconsi.electrobazar.model.Worker worker);

        Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
                        Customer customer,
                        com.proconsi.electrobazar.model.Worker worker);


        /**
         * Creates a sale with an explicit tariff override.
         * If tariffOverride is null, the customer's own tariff is used
         * (or MINORISTA if the customer has none).
         */
        Sale createSaleWithTariff(List<SaleLine> lines, PaymentMethod paymentMethod, String notes,
                        BigDecimal receivedAmount, Customer customer,
                        com.proconsi.electrobazar.model.Worker worker, Tariff tariffOverride);

        BigDecimal sumTotalToday();

        long countToday();

        BigDecimal sumTotalByPaymentMethodToday(PaymentMethod paymentMethod);

        com.proconsi.electrobazar.dto.SaleSummaryResponse getSummaryToday();

        /**
         * Persists the applyRecargo flag to an existing sale.
         *
         * @param saleId       The sale ID
         * @param applyRecargo Whether RE tax matches for this sale
         */
        void saveApplyRecargo(Long saleId, boolean applyRecargo);

        void cancelSale(Long id, com.proconsi.electrobazar.model.Worker worker, String reason);
}