package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.CashRegister;
import com.proconsi.electrobazar.model.CashWithdrawal;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.security.JwtService;
import com.proconsi.electrobazar.service.CashRegisterService;
import com.proconsi.electrobazar.service.CashWithdrawalService;
import com.proconsi.electrobazar.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * REST Controller for non-sale cash movements (Entries and Withdrawals).
 * Used for auditing cash drawer adjustments during a shift.
 * Requires 'CASH_CLOSE' permission.
 */
@RestController
@RequestMapping("/api/cash-withdrawals")
@RequiredArgsConstructor
public class CashWithdrawalApiRestController {

    private final CashWithdrawalService cashWithdrawalService;
    private final CashRegisterService cashRegisterService;
    private final WorkerService workerService;
    private final JwtService jwtService;

    /** Request body DTO for cash movements. */
    public record CashWithdrawalRequest(String amount, String reason, String type) { }

    /**
     * Records a new cash movement (Entry or Withdrawal) in the currently open register.
     * 
     * @param body Import and metadata for the movement.
     * @param authorizationHeader Bearer JWT token.
     * @return 201 Created with the movement details.
     */
    @PostMapping
    public ResponseEntity<?> createMovement(
            @RequestBody CashWithdrawalRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String token = authorizationHeader.substring(7);

        Long workerId;
        Set<String> permissions;
        try {
            Number workerIdNum = jwtService.extractClaim(token, claims -> claims.get("workerId", Number.class));
            workerId = workerIdNum != null ? workerIdNum.longValue() : null;
            @SuppressWarnings("unchecked")
            Set<String> rawPermissions = jwtService.extractClaim(token, claims -> claims.get("permissions", Set.class));
            permissions = rawPermissions;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
        }

        if (workerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token data"));
        }

        if (permissions == null || !permissions.contains("CASH_CLOSE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permiso para realizar movimientos de caja."));
        }

        Worker worker = workerService.findById(workerId)
                .orElseThrow(() -> new com.proconsi.electrobazar.exception.ResourceNotFoundException("Trabajador no encontrado"));

        CashRegister openRegister = cashRegisterService.getOpenRegister()
                .orElseThrow(() -> new IllegalStateException("No hay ninguna caja abierta."));

        if (body == null || body.amount == null || body.amount.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El importe es obligatorio."));
        }

        try {
            BigDecimal amountDecimal = new BigDecimal(body.amount.replace(",", "."));
            String typeValue = (body.type != null && !body.type.isBlank()) ? body.type : CashWithdrawal.MovementType.WITHDRAWAL.name();
            CashWithdrawal.MovementType movementType = CashWithdrawal.MovementType.valueOf(typeValue);

            CashWithdrawal movement = cashWithdrawalService.processMovement(
                    openRegister.getId(), amountDecimal, body.reason, movementType, worker);

            return ResponseEntity.status(HttpStatus.CREATED).body(movement);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
