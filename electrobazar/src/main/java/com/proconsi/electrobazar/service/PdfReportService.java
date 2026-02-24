package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.CashRegister;

import java.io.File;

public interface PdfReportService {

    /**
     * Generates a PDF report for the given closed cash register
     * and saves it to the configured directory.
     * 
     * @param register The closed CashRegister object
     * @return The File object representing the generated PDF
     */
    File generateCashCloseReport(CashRegister register);
}
