package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.SuspendedSaleLineRequest;
import com.proconsi.electrobazar.dto.SuspendedSaleResponse;
import com.proconsi.electrobazar.model.SuspendedSale;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.SuspendedSaleService;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.security.JwtService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for the Cart Reservation (Suspend/Resume) system.
 * Allows workers to 'park' a current sale and resume it later, or on a different device.
 * Requires HOLD_SALES permission.
 */
@RestController
@RequestMapping("/api/suspended-sales")
@RequiredArgsConstructor
public class SuspendedSaleApiRestController {

    private final SuspendedSaleService suspendedSaleService;
    private final WorkerService workerService;
    private final JwtService jwtService;

    /** Request body for suspending a cart. */
    public record SuspendRequest(List<SuspendedSaleLineRequest> lines, String label) { }

    /**
     * Lists all sales currently in 'SUSPENDED' status.
     * @return List of {@link SuspendedSaleResponse} DTOs.
     */
    @GetMapping
    public ResponseEntity<?> listSuspended(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        List<SuspendedSaleResponse> dtos = suspendedSaleService.findAllSuspended()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Temporarily saves a cart state.
     * @param body Cart lines and an optional identifying label.
     * @return 201 Created with the suspended sale DTO.
     */
    @PostMapping
    public ResponseEntity<?> suspend(@RequestBody SuspendRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        try {
            SuspendedSale saved = suspendedSaleService.suspend(body.lines(), body.label(), worker);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Resumes a previously suspended sale, making it available for checkout.
     * This marks the status as 'RESUMED'.
     * @param id The ID of the suspended sale.
     * @return 200 OK with full cart data.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resume(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        try {
            SuspendedSale resumed = suspendedSaleService.resume(id, worker);
            return ResponseEntity.ok(toResponse(resumed));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Discards a suspended sale without processing it.
     * @param id The ID to cancel.
     * @return 200 OK.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
             return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        try {
            SuspendedSale cancelled = suspendedSaleService.cancel(id, worker);
            return ResponseEntity.ok(toResponse(cancelled));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Worker authenticateAndGetWorker(String authorizationHeader, HttpSession session, String requiredPermission) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            try {
                Number workerIdNum = jwtService.extractClaim(token, claims -> claims.get("workerId", Number.class));
                Long workerId = workerIdNum != null ? workerIdNum.longValue() : null;
                @SuppressWarnings("unchecked")
                List<String> permissions = (List<String>) jwtService.extractClaim(token, claims -> claims.get("permissions", List.class));

                if (workerId != null && (requiredPermission == null || (permissions != null && permissions.contains(requiredPermission)))) {
                    return workerService.findById(workerId).orElse(null);
                }
            } catch (Exception ignored) { }
        }
        if (session != null) {
            Worker worker = (Worker) session.getAttribute("worker");
            if (worker != null && (requiredPermission == null || worker.getEffectivePermissions().contains(requiredPermission))) {
                return worker;
            }
        }
        return null;
    }

    private SuspendedSaleResponse toResponse(SuspendedSale sale) {
        List<SuspendedSaleResponse.SuspendedSaleLineResponse> lineResponses = sale.getLines() == null ? List.of()
                : sale.getLines().stream()
                        .map(line -> SuspendedSaleResponse.SuspendedSaleLineResponse.builder()
                                .productId(line.getProduct().getId())
                                .productName(line.getProduct().getName())
                                .quantity(line.getQuantity())
                                .unitPrice(line.getUnitPrice())
                                .build())
                        .collect(Collectors.toList());

        return SuspendedSaleResponse.builder()
                .id(sale.getId())
                .label(sale.getLabel())
                .status(sale.getStatus() != null ? sale.getStatus().name() : null)
                .createdAt(sale.getCreatedAt())
                .workerUsername(sale.getWorker() != null ? sale.getWorker().getUsername() : null)
                .lines(lineResponses)
                .build();
    }
}
