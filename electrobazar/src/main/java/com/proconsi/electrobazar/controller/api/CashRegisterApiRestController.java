package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.service.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.proconsi.electrobazar.dto.CashCloseInfoDTO;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cash-registers")
@RequiredArgsConstructor
public class CashRegisterApiRestController {

    private final CashRegisterService cashRegisterService;
    private final com.proconsi.electrobazar.service.PdfReportService pdfReportService;
    private final com.proconsi.electrobazar.service.WorkerService workerService;
    private final com.proconsi.electrobazar.service.SaleService saleService;
    private final com.proconsi.electrobazar.service.ReturnService returnService;
    private final com.proconsi.electrobazar.service.CashWithdrawalService cashWithdrawalService;

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

    @GetMapping("/close-info")
    public ResponseEntity<CashCloseInfoDTO> getCloseInfo() {
        Optional<CashRegister> openRegisterOpt = cashRegisterService.getOpenRegister();
        if (openRegisterOpt.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        CashRegister openRegister = openRegisterOpt.get();
        BigDecimal cashSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CASH);
        BigDecimal cashRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CASH);
        BigDecimal cardSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CARD);
        BigDecimal cardRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CARD);

        List<CashWithdrawal> movements = cashWithdrawalService.findByRegisterId(openRegister.getId());
        BigDecimal totalWithdrawals = movements.stream()
                .filter(m -> m.getType() == null || m.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEntries = movements.stream()
                .filter(m -> m.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startOfShift = openRegister.getOpeningTime() != null
                ? openRegister.getOpeningTime()
                : java.time.LocalDate.now().atStartOfDay();

        BigDecimal expectedCashInDrawer = openRegister.getOpeningBalance()
                .add(cashSalesToday)
                .add(totalEntries)
                .subtract(totalWithdrawals)
                .subtract(cashRefundsToday);

        SaleSummaryResponse summary = saleService.getSummaryToday();

        CashCloseInfoDTO info = CashCloseInfoDTO.builder()
                .todayRegister(openRegister)
                .totalToday(saleService.sumTotalToday())
                .countToday(saleService.countToday())
                .cashSalesToday(cashSalesToday)
                .cashRefundsToday(cashRefundsToday)
                .cardSalesToday(cardSalesToday)
                .cardRefundsToday(cardRefundsToday)
                .totalWithdrawals(totalWithdrawals)
                .totalEntries(totalEntries)
                .expectedCashInDrawer(expectedCashInDrawer)
                .cancelledCount(summary.getTotalCancelledCount())
                .cancelledTotal(summary.getTotalCancelledAmount())
                .returnsToday(returnService.findByCreatedAtBetween(startOfShift, LocalDateTime.now()))
                .openingBalance(openRegister.getOpeningBalance())
                .build();

        return ResponseEntity.ok(info);
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
