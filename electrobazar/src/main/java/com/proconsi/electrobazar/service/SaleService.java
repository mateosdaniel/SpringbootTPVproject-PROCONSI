package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleLine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleService {
        Sale findById(Long id);

        List<Sale> findToday();

        List<Sale> findBetween(LocalDateTime from, LocalDateTime to);

        Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
                        com.proconsi.electrobazar.model.Worker worker);

        Sale createSale(List<SaleLine> lines, PaymentMethod paymentMethod, String notes, BigDecimal receivedAmount,
                        Customer customer,
                        com.proconsi.electrobazar.model.Worker worker);

        BigDecimal sumTotalToday();

        long countToday();

        BigDecimal sumTotalByPaymentMethodToday(PaymentMethod paymentMethod);
}