package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.Tariff;
import com.proconsi.electrobazar.dto.TariffPriceEntryDTO;
import java.util.List;
import java.time.LocalDate;

/**
 * Interface responsible for generating PDF documents for various reports.
 */
public interface PdfReportService {

    /**
     * Generates a PDF report summarizing a closed cash register shift.
     *
     * @param register The closed CashRegister entity.
     * @return The PDF data as a byte array.
     */
    byte[] generateCashCloseReport(CashRegister register);

    /**
     * Generates a PDF sheet displaying prices for a specific tariff at a given date.
     *
     * @param tariff  The target tariff.
     * @param history The list of prices to include in the sheet.
     * @param date    The validity date of the tariff records.
     * @return The PDF data as a byte array.
     */
    byte[] generateTariffSheet(Tariff tariff, List<TariffPriceEntryDTO> history, LocalDate date);
}
