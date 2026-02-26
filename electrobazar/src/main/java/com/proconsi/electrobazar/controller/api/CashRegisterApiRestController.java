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

    @GetMapping("/{id}/ticket")
    public ResponseEntity<org.springframework.core.io.Resource> getTicket(@PathVariable Long id) {
        CashRegister cr = cashRegisterService.findById(id);
        java.io.File pdfFile = pdfReportService.generateCashCloseReport(cr);
        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(pdfFile);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdfFile.getName() + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @PostMapping("/close")
    public ResponseEntity<CashRegister> closeCashRegister(
            @RequestParam BigDecimal closingBalance,
            @RequestParam(required = false) String notes,
            jakarta.servlet.http.HttpSession session) {
        com.proconsi.electrobazar.model.Worker worker = (com.proconsi.electrobazar.model.Worker) session
                .getAttribute("worker");
        CashRegister cr = cashRegisterService.closeCashRegister(closingBalance, notes, worker);
        pdfReportService.generateCashCloseReport(cr);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cr);
    }
}
