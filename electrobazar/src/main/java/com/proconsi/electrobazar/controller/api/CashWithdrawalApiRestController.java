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
import java.util.Optional;
import java.util.Set;

/**
 * REST wrapper for cash movements (withdrawals / entries) from the TPV.
 *
 * Mirrors the behavior of {@code TpvController.processWithdrawal}, but exposes
 * it as a JSON API:
 *
 * <ul>
 *   <li>Uses the currently open {@link CashRegister}.</li>
 *   <li>Parses {@code amount} and {@code type} in the same way as the MVC controller.</li>
 *   <li>Resolves the {@link Worker} from the JWT {@code workerId} claim.</li>
 *   <li>Requires the {@code CASH_CLOSE} permission present in the JWT.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cash-withdrawals")
@RequiredArgsConstructor
public class CashWithdrawalApiRestController {

    private final CashWithdrawalService cashWithdrawalService;
    private final CashRegisterService cashRegisterService;
    private final WorkerService workerService;
    private final JwtService jwtService;

    /**
     * Request body for creating a cash movement.
     * Fields mirror those used by {@code TpvController.processWithdrawal}.
     */
    public record CashWithdrawalRequest(
            String amount,
            String reason,
            String type) {
    }

    @PostMapping
    public ResponseEntity<?> createMovement(
            @RequestBody CashWithdrawalRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
        }

        String token = authorizationHeader.substring(7);

        // Extract workerId and permissions from JWT (same pattern as JwtAuthenticationFilter)
        Long workerId;
        Set<String> permissions;
        try {
            workerId = jwtService.extractClaim(token, claims -> claims.get("workerId", Long.class));
            @SuppressWarnings("unchecked")
            Set<String> rawPermissions = jwtService.extractClaim(token,
                    claims -> claims.get("permissions", Set.class));
            permissions = rawPermissions;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid JWT token"));
        }

        if (workerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token does not contain workerId"));
        }

        if (permissions == null || !permissions.contains("CASH_CLOSE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permiso para realizar movimientos de caja."));
        }

        Optional<Worker> workerOpt = workerService.findById(workerId);
        if (workerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Trabajador no encontrado para el token proporcionado"));
        }
        Worker worker = workerOpt.get();

        Optional<CashRegister> openRegisterOpt = cashRegisterService.getOpenRegister();
        if (openRegisterOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No hay ninguna caja abierta."));
        }

        if (body == null || body.amount == null || body.amount.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El importe es obligatorio."));
        }

        try {
            String normalizedAmount = body.amount.replace(",", ".");
            BigDecimal amountDecimal = new BigDecimal(normalizedAmount);

            String typeValue = body.type != null && !body.type.isBlank()
                    ? body.type
                    : CashWithdrawal.MovementType.WITHDRAWAL.name();

            CashWithdrawal.MovementType movementType = CashWithdrawal.MovementType
                    .valueOf(typeValue);

            CashWithdrawal movement = cashWithdrawalService.processMovement(
                    openRegisterOpt.get().getId(),
                    amountDecimal,
                    body.reason,
                    movementType,
                    worker);

            return ResponseEntity.status(HttpStatus.CREATED).body(movement);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar el movimiento: " + e.getMessage()));
        }
    }
}

