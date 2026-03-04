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

        List<Sale> findAll();

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

        com.proconsi.electrobazar.dto.SaleSummaryResponse getSummaryToday();

        /**
         * Saves the PDF blob and filename into an existing Sale (ticket).
         *
         * @param saleId   The sale ID
         * @param pdfData  The PDF bytes
         * @param filename The filename
         */
        void savePdf(Long saleId, byte[] pdfData, String filename);

        /**
         * Retrieves only the PDF bytes for the given sale ID.
         *
         * @param saleId The sale ID
         * @return The PDF bytes or null if not found
         */
        byte[] getPdfData(Long saleId);
}