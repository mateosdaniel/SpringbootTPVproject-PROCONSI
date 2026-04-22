package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.dto.ReturnLineRequest;
import com.proconsi.electrobazar.model.PaymentMethod;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.SaleReturn;
import com.proconsi.electrobazar.model.Worker;
import com.proconsi.electrobazar.security.JwtService;
import com.proconsi.electrobazar.service.InvoiceService;
import com.proconsi.electrobazar.service.ReturnService;
import com.proconsi.electrobazar.service.SaleService;
import com.proconsi.electrobazar.service.TicketService;
import com.proconsi.electrobazar.service.WorkerService;
import com.proconsi.electrobazar.exception.InsufficientCashException;
import com.proconsi.electrobazar.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for handling product returns.
 * Delegates complex fiscal logic (rectificative invoices, stock restoration) to the ReturnService.
 * Requires the 'RETURNS' permission in the JWT token.
 */
@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
public class ReturnApiRestController {

    private final SaleService saleService;
    private final TicketService ticketService;
    private final InvoiceService invoiceService;
    private final ReturnService returnService;
    private final WorkerService workerService;
    private final JwtService jwtService;

    /** Response DTO for ticket capability checks. */
    public record ReturnCheckResponse(Long saleId, boolean canReturn) { }

    /** Request DTO for processing a return. */
    public record ReturnRequest(
            Long saleId,
            List<ReturnLineRequest> lines,
            String reason,
            PaymentMethod paymentMethod) { }

    /**
     * Checks if a ticket exists and is eligible for a return.
     * Searches by numeric Sale ID first, then by alphanumeric Ticket Number.
     * 
     * @param query The ID or Ticket Number.
     * @return 200 OK with saleId if found.
     */
    @GetMapping("/check")
    public ResponseEntity<?> checkTicketForReturn(@RequestParam String query) {
        try {
            Long saleId = null;

            if (query.matches("\\d+")) {
                Long id = Long.parseLong(query);
                Sale sale = saleService.findById(id);
                if (sale != null) {
                    saleId = id;
                }
            }

            if (saleId == null) {
                saleId = ticketService.findByTicketNumber(query)
                        .map(t -> t.getSale().getId())
                        .orElse(null);
            }

            if (saleId == null) {
                saleId = invoiceService.findByInvoiceNumber(query)
                        .map(i -> i.getSale().getId())
                        .orElse(null);
            }

            if (saleId != null) {
                return ResponseEntity.ok(new ReturnCheckResponse(saleId, true));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("errorMessage", "Ticket o Factura no encontrado: " + query));
            }
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("errorMessage", "Error al buscar el ticket/factura: " + e.getMessage()));
        }
    }

    /**
     * Processes a return transaction.
     * Ensures the worker has sufficient permissions and that the cash drawer can cover the refund.
     * 
     * @param request Return details (lines, amounts, payment method).
     * @param authorizationHeader Bearer JWT token.
     * @return 201 Created with the generated {@link SaleReturn}.
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
            List<String> rawPermissions = jwtService.extractClaim(token, claims -> claims.get("permissions", List.class));
            permissions = rawPermissions;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid JWT token"));
        }

        if (workerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token does not contain workerId"));
        }

        if (permissions == null || !permissions.contains("RETURNS")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tiene permiso para procesar devoluciones."));
        }

        Worker worker = workerService.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador no encontrado"));

        try {
            SaleReturn saleReturn = returnService.processReturn(
                    request.saleId, request.lines, request.reason, request.paymentMethod, worker);
            return ResponseEntity.status(HttpStatus.CREATED).body(saleReturn);
        } catch (IllegalArgumentException | InsufficientCashException e) {
            return ResponseEntity.badRequest().body(Map.of("errorMessage", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("errorMessage", "Error al procesar la devolución: " + e.getMessage()));
        }
    }

    /**
     * Retrieves all returns within a specified time range for auditing.
     * @return List of {@link SaleReturn} entities.
     */
    @GetMapping
    public ResponseEntity<?> getAllReturns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        LocalDateTime startTime = from != null ? from : LocalDateTime.now().minusYears(1);
        LocalDateTime endTime = to != null ? to : LocalDateTime.now();
        return ResponseEntity.ok(returnService.findByCreatedAtBetweenWithDetails(startTime, endTime));
    }

    /**
     * Retrieves specific return details.
     * @param id The return ID.
     * @return The requested {@link SaleReturn}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getReturnById(@PathVariable Long id) {
        return returnService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
