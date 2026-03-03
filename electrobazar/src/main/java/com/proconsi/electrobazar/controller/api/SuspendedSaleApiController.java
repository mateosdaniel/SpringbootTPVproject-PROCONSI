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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for the cart suspend/resume system.
 * All endpoints require an active session worker and return 401 otherwise.
 * All successful responses use {@link SuspendedSaleResponse} — never the raw
 * entity.
 */
@RestController
@RequestMapping("/tpv/suspended-sales")
@RequiredArgsConstructor
public class SuspendedSaleApiController {

    private final SuspendedSaleService suspendedSaleService;

    // ── Request body DTO ───────────────────────────────────────────────────────

    /** Body shape for the suspend endpoint: { lines: [...], label: "..." } */
    public record SuspendRequest(List<SuspendedSaleLineRequest> lines, String label) {
    }

    // ── Endpoints ──────────────────────────────────────────────────────────────

    /**
     * GET /tpv/suspended-sales
     * Returns all sales currently in SUSPENDED status, newest first.
     */
    @GetMapping
    public ResponseEntity<?> listSuspended(HttpSession session) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No hay sesión activa"));
        }
        List<SuspendedSaleResponse> dtos = suspendedSaleService.findAllSuspended()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * POST /tpv/suspended-sales
     * Persists the current cart and returns the new suspended sale as a DTO.
     */
    @PostMapping
    public ResponseEntity<?> suspend(@RequestBody SuspendRequest body, HttpSession session) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No hay sesión activa"));
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
     * POST /tpv/suspended-sales/{id}/resume
     * Marks the sale as RESUMED and returns full line data so the JS can reload the
     * cart.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resume(@PathVariable Long id, HttpSession session) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No hay sesión activa"));
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
     * POST /tpv/suspended-sales/{id}/cancel
     * Marks the sale as CANCELLED (no stock movement needed).
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, HttpSession session) {
        Worker worker = (Worker) session.getAttribute("worker");
        if (worker == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No hay sesión activa"));
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

    // ── Mapper ─────────────────────────────────────────────────────────────────

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
