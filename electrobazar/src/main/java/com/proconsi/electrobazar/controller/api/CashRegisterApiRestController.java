package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.CashRegisterService;
import com.proconsi.electrobazar.service.CashSessionService;
import com.proconsi.electrobazar.service.PdfReportService;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.service.ReturnService;
import com.proconsi.electrobazar.service.CashWithdrawalService;
import com.proconsi.electrobazar.dto.CashCloseInfoDTO;
import com.proconsi.electrobazar.dto.SaleSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REST Controller for managing Cash Register operations.
 * Handles the opening, closing, and auditing of daily cash sessions.
 */
@RestController
@RequestMapping("/api/cash-registers")
@RequiredArgsConstructor
public class CashRegisterApiRestController {

    private final CashSessionService cashSessionService;
    private final CashRegisterService cashRegisterService;
    private final PdfReportService pdfReportService;
    private final WorkerService workerService;
    private final SaleService saleService;
    private final ReturnService returnService;
    private final CashWithdrawalService cashWithdrawalService;

    /**
     * Retrieves the details of a specific cash register session.
     * 
     * @param id Internal ID of the cash register.
     * @return The {@link CashRegister} entity.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CashRegister> getById(@PathVariable Long id) {
        return ResponseEntity.ok(cashSessionService.findById(id));
    }

    /**
     * Retrieves all historically closed cash sessions.
     * 
     * @return List of closed {@link CashRegister} sessions.
     */
    @GetMapping("/closed")
    public ResponseEntity<List<CashRegister>> getAllClosed() {
        return ResponseEntity.ok(cashSessionService.findAllClosed());
    }

    /**
     * Retrieves the currently active (open) cash register session, if any.
     * 
     * @return 200 with the active {@link CashRegister}, or 204 if none are open.
     */
    @GetMapping("/open")
    public ResponseEntity<CashRegister> getOpen() {
        return cashSessionService.getActiveSession()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Gathers all financial data required to perform a cash close for the active
     * session.
     * Calculates expected cash in drawer by accounting for sales, returns, and
     * manual entries/withdrawals.
     * 
     * @return Detailed {@link CashCloseInfoDTO}.
     */
    @GetMapping("/close-info")
    public ResponseEntity<CashCloseInfoDTO> getCloseInfo() {
        Optional<CashRegister> activeSessionOpt = cashSessionService.getActiveSession();
        if (activeSessionOpt.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        CashRegister activeSession = activeSessionOpt.get();
        BigDecimal cashSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CASH);
        BigDecimal cashRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CASH);
        BigDecimal cardSalesToday = saleService.sumTotalByPaymentMethodToday(PaymentMethod.CARD);
        BigDecimal cardRefundsToday = returnService.sumTotalRefundedTodayByPaymentMethod(PaymentMethod.CARD);

        List<CashWithdrawal> movements = cashWithdrawalService.findBySessionId(activeSession.getId());
        BigDecimal totalWithdrawals = movements.stream()
                .filter(m -> m.getType() == null || m.getType() == CashWithdrawal.MovementType.WITHDRAWAL)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEntries = movements.stream()
                .filter(m -> m.getType() == CashWithdrawal.MovementType.ENTRY)
                .map(m -> m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime startOfShift = activeSession.getOpeningTime();

        // Compatibility logic for expected cash
        BigDecimal expectedCashInDrawer = activeSession.getClosingBalance();

        SaleSummaryResponse summary = saleService.getSummaryToday();

        CashCloseInfoDTO info = CashCloseInfoDTO.builder()
                .activeSession(activeSession)
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
                .openingBalance(activeSession.getOpeningBalance())
                .build();

        return ResponseEntity.ok(info);
    }

    /**
     * Retrieves today's cash session, whether open or recently closed.
     * 
     * @return Today's {@link CashRegister}.
     */
    @GetMapping("/today")
    public ResponseEntity<CashRegister> getToday() {
        return cashSessionService.getActiveSession()
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    try {
                        return ResponseEntity.ok(cashSessionService.findTodayIfClosed());
                    } catch (Exception e) {
                        return ResponseEntity.noContent().build();
                    }
                });
    }

    /**
     * Generates a PDF closing report for a specific cash session.
     * 
     * @param id ID of the cash register.
     * @return PDF file as a downloadable resource.
     */
    @GetMapping("/{id}/ticket")
    public ResponseEntity<Resource> getTicket(@PathVariable Long id) {
        CashRegister cs = cashSessionService.findById(id);
        byte[] pdfData = pdfReportService.generateCashCloseReport(cs);

        String filename = "Cierre_Caja_" + id + ".pdf";
        Resource resource = new ByteArrayResource(pdfData);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    /**
     * Initializes a new cash session.
     * 
     * @param openingBalance The initial cash money available in the drawer.
     * @param workerId       ID of the worker opening the register.
     * @return 201 Created with the new session details.
     */
    @PostMapping("/open")
    public ResponseEntity<CashRegister> openCashRegister(
            @RequestParam BigDecimal openingBalance,
            @RequestHeader(value = "X-Worker-Id", required = false) Long workerId) {

        Worker worker = null;
        if (workerId != null) {
            worker = workerService.findById(workerId).orElse(null);
        }
        try {
            CashRegister cs = cashSessionService.openSession(openingBalance, worker);
            return ResponseEntity.status(HttpStatus.CREATED).body(cs);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Closes the active cash session.
     * Automatically calculates the difference between expected and actual cash.
     * 
     * @param closingBalance The physical cash counted in the drawer at the end of
     *                       the shift.
     * @param notes          Optional notes about the session (e.g., explaining
     *                       discrepancies).
     * @param retainedAmount Amount of money to keep in the drawer for the next
     *                       shift.
     * @param workerId       ID of the worker closing the register.
     * @return 201 Created with the closed session and audit details.
     */
    @PostMapping("/close")
    public ResponseEntity<CashRegister> closeCashRegister(
            @RequestParam BigDecimal closingBalance,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) BigDecimal retainedAmount,
            @RequestHeader(value = "X-Worker-Id", required = false) Long workerId) {

        Worker worker = null;
        if (workerId != null) {
            worker = workerService.findById(workerId).orElse(null);
        }

        // Use CashRegisterService which handles all calculations (sales, returns,
        // withdrawals, etc.)
        CashRegister cs = cashRegisterService.closeCashRegister(closingBalance, notes, worker, retainedAmount);

        try {
            pdfReportService.generateCashCloseReport(cs);
        } catch (Exception e) {
            // Log error but don't fail the response, as core business logic succeeded
            System.err.println("Error generating PDF at close: " + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cs);
    }
}
