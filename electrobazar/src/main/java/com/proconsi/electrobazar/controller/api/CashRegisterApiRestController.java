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

    @GetMapping("/{id}")
    public ResponseEntity<CashRegister> getById(@PathVariable Long id) {
        return ResponseEntity.ok(cashRegisterService.findById(id));
    }

    @GetMapping("/closed")
    public ResponseEntity<List<CashRegister>> getAllClosed() {
        return ResponseEntity.ok(cashRegisterService.findAllClosed());
    }

    @GetMapping("/today")
    public ResponseEntity<CashRegister> getToday() {
        return ResponseEntity.ok(cashRegisterService.getTodayRegister());
    }

    @PostMapping("/close")
    public ResponseEntity<CashRegister> closeCashRegister(
            @RequestParam BigDecimal closingBalance,
            @RequestParam(required = false) String notes,
            jakarta.servlet.http.HttpSession session) {
        com.proconsi.electrobazar.model.Worker worker = (com.proconsi.electrobazar.model.Worker) session
                .getAttribute("worker");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cashRegisterService.closeCashRegister(closingBalance, notes, worker));
    }
}
