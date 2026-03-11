package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.SuspendedSaleLineRequest;
import com.proconsi.electrobazar.dto.SuspendedSaleResponse;
import com.proconsi.electrobazar.model.SuspendedSale;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.service.SuspendedSaleService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.proconsi.electrobazar.security.JwtService;
import com.proconsi.electrobazar.service.WorkerService;
import java.util.Optional;
import java.util.Set;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for the cart suspend/resume system.
 * All endpoints require a valid JWT with HOLD_SALES permission and return
 * 401/403 otherwise.
 * All successful responses use {@link SuspendedSaleResponse} — never the raw
 * entity.
 */
@RestController
@RequestMapping("/api/suspended-sales")
@RequiredArgsConstructor
public class SuspendedSaleApiRestController {

    private final SuspendedSaleService suspendedSaleService;
    private final WorkerService workerService;
    private final JwtService jwtService;

    // ── Request body DTO ───────────────────────────────────────────────────────

    /** Body shape for the suspend endpoint: { lines: [...], label: "..." } */
    public record SuspendRequest(List<SuspendedSaleLineRequest> lines, String label) {
    }

    // ── Endpoints ──────────────────────────────────────────────────────────────

    /**
     * GET /api/suspended-sales
     * Returns all sales currently in SUSPENDED status, newest first.
     */
    @GetMapping
    public ResponseEntity<?> listSuspended(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Falta token o sesión inválida"));
        }
        List<SuspendedSaleResponse> dtos = suspendedSaleService.findAllSuspended()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /api/suspended-sales
     * Persists the current cart and returns the new suspended sale as a DTO.
     */
    @PostMapping
    public ResponseEntity<?> suspend(@RequestBody SuspendRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Falta token o sesión inválida"));
        }
        try {
            SuspendedSale saved = suspendedSaleService.suspend(body.lines(), body.label(), worker);
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }

    /**
     * POST /api/suspended-sales/{id}/resume
     * Marks the sale as RESUMED and returns full line data so the JS can reload the
     * cart.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resume(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Falta token o sesión inválida"));
        }
        try {
            SuspendedSale resumed = suspendedSaleService.resume(id, worker);
            return ResponseEntity.ok(toResponse(resumed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }

    /**
     * POST /api/suspended-sales/{id}/cancel
     * Marks the sale as CANCELLED (no stock movement needed).
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpSession session) {
        Worker worker = authenticateAndGetWorker(authorizationHeader, session, "HOLD_SALES");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Falta token o sesión inválida"));
        }
        try {
            SuspendedSale cancelled = suspendedSaleService.cancel(id, worker);
            return ResponseEntity.ok(toResponse(cancelled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }

    // ── Utils & Mapper ────────────────────────────────────────────────────────

    private Worker authenticateAndGetWorker(String authorizationHeader, HttpSession session, String requiredPermission) {
        // 1. Try JWT (External TPV/JavaFX)
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            try {
                Long workerId = jwtService.extractClaim(token, claims -> claims.get("workerId", Long.class));
                @SuppressWarnings("unchecked")
                Set<String> permissions = jwtService.extractClaim(token, claims -> claims.get("permissions", Set.class));

                if (workerId != null && (requiredPermission == null || (permissions != null && permissions.contains(requiredPermission)))) {
                    return workerService.findById(workerId).orElse(null);
                }
            } catch (Exception e) {
                // FALL THROUGH to session check
            }
        }

        // 2. Try Session (Web Frontend)
        if (session != null) {
            Worker worker = (Worker) session.getAttribute("worker");
            if (worker != null) {
                if (requiredPermission == null || worker.getEffectivePermissions().contains(requiredPermission)) {
                    return worker;
                }
            }
        }

        return null;
    }

    /**
     * Converts a {@link SuspendedSale} entity to a safe
     * {@link SuspendedSaleResponse} DTO.
     * All field accesses use the already-loaded state; no lazy collections are
     * triggered
     * beyond what was already fetched by the service (via @EntityGraph on findById,
     * or the cascade-loaded lines from the suspend/resume operations).
     */
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
