package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.service.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cash-registers")
@RequiredArgsConstructor
public class CashRegisterApiRestController {

    private final CashRegisterService cashRegisterService;
    private final com.proconsi.electrobazar.service.PdfReportService pdfReportService;
    private final com.proconsi.electrobazar.service.WorkerService workerService;

    @GetMapping("/{id}")
    public ResponseEntity<CashRegister> getById(@PathVariable Long id) {
        return ResponseEntity.ok(cashRegisterService.findById(id));
    }

    @GetMapping("/closed")
    public ResponseEntity<List<CashRegister>> getAllClosed() {
        return ResponseEntity.ok(cashRegisterService.findAllClosed());
    }

    @GetMapping("/open")
    public ResponseEntity<CashRegister> getOpen() {
        return cashRegisterService.getOpenRegister()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/today")
    public ResponseEntity<CashRegister> getToday() {
        return cashRegisterService.getOpenRegister()
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    try {
                        return ResponseEntity.ok(cashRegisterService.findTodayIfClosed());
                    } catch (Exception e) {
                        return ResponseEntity.noContent().build();
                    }
                });
    }

    @GetMapping("/{id}/ticket")
    public ResponseEntity<org.springframework.core.io.Resource> getTicket(@PathVariable Long id) {
        CashRegister cr = cashRegisterService.findById(id);
        byte[] pdfData = pdfReportService.generateCashCloseReport(cr);

        String filename = "Cierre_Caja_" + id + ".pdf";
        org.springframework.core.io.Resource resource = new org.springframework.core.io.ByteArrayResource(pdfData);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @PostMapping("/open")
    public ResponseEntity<CashRegister> openCashRegister(
            @RequestParam BigDecimal openingBalance,
            @RequestHeader(value = "X-Worker-Id", required = false) Long workerId) {

        com.proconsi.electrobazar.model.Worker worker = null;
        if (workerId != null) {
            worker = workerService.findById(workerId).orElse(null);
        }
        try {
            CashRegister cr = cashRegisterService.openCashRegister(openingBalance, worker);
            return ResponseEntity.status(HttpStatus.CREATED).body(cr);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/close")
    public ResponseEntity<CashRegister> closeCashRegister(
            @RequestParam BigDecimal closingBalance,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) BigDecimal retainedAmount,
            @RequestHeader(value = "X-Worker-Id", required = false) Long workerId) {

        com.proconsi.electrobazar.model.Worker worker = null;
        if (workerId != null) {
            worker = workerService.findById(workerId).orElse(null);
        }
        CashRegister cr = cashRegisterService.closeCashRegister(closingBalance, notes, worker, retainedAmount);
        try {
            pdfReportService.generateCashCloseReport(cr);
        } catch (Exception e) {
            // Log error but don't fail the response, as core business logic succeeded
            System.err.println("Error generating PDF at close: " + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cr);
    }
}
