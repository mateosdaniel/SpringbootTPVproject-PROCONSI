package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.ReturnLineRequest;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleReturn;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.security.JwtService;
import com.proconsi.electrobazar.service.ReturnService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.service.TicketService;
import com.proconsi.electrobazar.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REST API for handling returns from external TPV clients.
 *
 * Mirrors the behavior of the TPV MVC return flow in {@link com.proconsi.electrobazar.controller.web.TpvController}:
 * <ul>
 *   <li>Ticket lookup logic from {@code /tpv/return/check}.</li>
 *   <li>Return processing via {@link ReturnService#processReturn(Long, List, String, PaymentMethod, Worker)}.</li>
 *   <li>Worker identity and permissions resolved from JWT ({@code workerId}, {@code permissions}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnApiRestController {

    private final SaleService saleService;
    private final TicketService ticketService;
    private final ReturnService returnService;
    private final WorkerService workerService;
    private final JwtService jwtService;

    /**
     * Lightweight DTO for checking whether a ticket/sale can be returned.
     */
    public record ReturnCheckResponse(Long saleId, boolean canReturn) {
    }

    /**
     * Request body for processing a return.
     * Wraps the same data that {@code TpvController.processReturn} expects.
     */
    public record ReturnRequest(
            Long saleId,
            List<ReturnLineRequest> lines,
            String reason,
            PaymentMethod paymentMethod) {
    }

    /**
     * GET /api/returns/check?query=...
     *
     * Mirrors TpvController.checkTicketForReturn, but returns a JSON payload:
     * { "saleId": 123, "canReturn": true } on success, or a JSON error on failure.
     */
    @GetMapping("/check")
    public ResponseEntity<?> checkTicketForReturn(@RequestParam String query) {
        try {
            Long saleId = null;

            // 1. Try searching by numeric sale ID
            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                Sale sale = saleService.findById(id);
                if (sale != null) {
                    saleId = id;
                }
            }

            // 2. Try searching by Ticket Number
            if (saleId == null) {
                saleId = ticketService.findByTicketNumber(query)
                        .map(t -> t.getSale().getId())
                        .orElse(null);
            }

            if (saleId != null) {
                return ResponseEntity.ok(new ReturnCheckResponse(saleId, true));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("errorMessage", "Ticket no encontrado: " + query));
            }
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("errorMessage", "Error al buscar el ticket: " + e.getMessage()));
        }
    }

    /**
     * POST /api/returns
     *
     * Processes a return for a given sale, delegating to {@link ReturnService}.
     * The worker is resolved from the JWT token and must have the {@code RETURNS}
     * permission.
     */
    @PostMapping
    public ResponseEntity<?> processReturn(
            @RequestBody ReturnRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
        }

        if (request == null || request.saleId == null || request.lines == null || request.lines.isEmpty()
                || request.paymentMethod == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "saleId, lines y paymentMethod son obligatorios"));
        }

        String token = authorizationHeader.substring(7);

        Long workerId;
        List<String> permissions;
        try {
            Number workerIdNum = jwtService.extractClaim(token, claims -> claims.get("workerId", Number.class));
            workerId = workerIdNum != null ? workerIdNum.longValue() : null;
            @SuppressWarnings("unchecked")
            List<String> rawPermissions = jwtService.extractClaim(token,
                    claims -> claims.get("permissions", List.class));
            permissions = rawPermissions;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid JWT token"));
        }

        if (workerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token does not contain workerId"));
        }

        if (permissions == null || !permissions.contains("RETURNS")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permiso para procesar devoluciones."));
        }

        Optional<Worker> workerOpt = workerService.findById(workerId);
        if (workerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Trabajador no encontrado para el token proporcionado"));
        }
        Worker worker = workerOpt.get();

        try {
            SaleReturn saleReturn = returnService.processReturn(
                    request.saleId,
                    request.lines,
                    request.reason,
                    request.paymentMethod,
                    worker);
            return ResponseEntity.status(HttpStatus.CREATED).body(saleReturn);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("errorMessage", e.getMessage()));
        } catch (com.proconsi.electrobazar.exception.InsufficientCashException e) {
            return ResponseEntity.badRequest().body(Map.of("errorMessage", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("errorMessage", "Error al procesar la devolución: " + e.getMessage()));
        }
    }
    /**
     * GET /api/returns
     *
     * Returns a list of all returns, optionally filtered by date range.
     * Requires ADMIN_ACCESS or similar elevated permissions.
     */
    @GetMapping
    public ResponseEntity<?> getAllReturns(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime to) {
        
        java.time.LocalDateTime startTime = from != null ? from : java.time.LocalDateTime.now().minusYears(1);
        java.time.LocalDateTime endTime = to != null ? to : java.time.LocalDateTime.now();

        List<SaleReturn> returns = returnService.findByCreatedAtBetween(startTime, endTime);
        return ResponseEntity.ok(returns);
    }

    /**
     * GET /api/returns/{id}
     *
     * Returns the details of a specific return.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getReturnById(@PathVariable Long id) {
        return returnService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

