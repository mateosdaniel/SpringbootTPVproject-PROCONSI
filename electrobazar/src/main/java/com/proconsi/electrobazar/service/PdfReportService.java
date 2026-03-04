package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.Sale;

public interface PdfReportService {

    /**
     * Generates a PDF report for the given closed cash register
     * and returns the bytes.
     *
     * @param register The closed CashRegister object
     * @return The PDF data as byte array
     */
    byte[] generateCashCloseReport(CashRegister register);

    /**
     * Generates a PDF invoice report for the given sale and its associated invoice.
     * Includes detailed tax breakdown for fiscal transparency.
     *
     * @param sale          The Sale object
     * @param invoice       The Invoice object
     * @param taxBreakdowns List of tax breakdowns
     * @param applyRecargo  Whether recargo de equivalencia applies
     * @param totalBase     Total taxable base
     * @param totalVat      Total vat amount
     * @param totalRecargo  Total recargo amount
     * @return The PDF data as byte array
     */
    byte[] generateInvoiceReport(
            Sale sale,
            Invoice invoice,
            java.util.List<com.proconsi.electrobazar.dto.TaxBreakdown> taxBreakdowns,
            Boolean applyRecargo,
            java.math.BigDecimal totalBase,
            java.math.BigDecimal totalVat,
            java.math.BigDecimal totalRecargo);

    /**
     * Generates a PDF ticket report for the given sale and tax breakdowns.
     *
     * @param sale          The Sale object
     * @param taxBreakdowns List of tax breakdowns
     * @param applyRecargo  Whether recargo de equivalencia applies
     * @param totalBase     Total taxable base
     * @param totalVat      Total vat amount
     * @param totalRecargo  Total recargo amount
     * @return The PDF data as byte array
     */
    byte[] generateTicketReport(
            Sale sale,
            java.util.List<com.proconsi.electrobazar.dto.TaxBreakdown> taxBreakdowns,
            Boolean applyRecargo,
            java.math.BigDecimal totalBase,
            java.math.BigDecimal totalVat,
            java.math.BigDecimal totalRecargo);
}
