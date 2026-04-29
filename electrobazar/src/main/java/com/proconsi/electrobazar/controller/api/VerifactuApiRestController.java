package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.AeatStatus;
import com.proconsi.electrobazar.model.Invoice;
import com.proconsi.electrobazar.model.RectificativeInvoice;
import com.proconsi.electrobazar.model.Ticket;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.RectificativeInvoiceRepository;
import com.proconsi.electrobazar.repository.TicketRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for VeriFactu / AEAT submission status dashboard.
 * Covers invoices (facturas), rectificative invoices (facturas rectificativas)
 * and simplified tickets (tickets).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/verifactu")
public class VerifactuApiRestController {

    private final InvoiceRepository              invoiceRepository;
    private final RectificativeInvoiceRepository rectificativeRepository;
    private final TicketRepository               ticketRepository;
    private final com.proconsi.electrobazar.service.VerifactuService verifactuService;
    private final com.proconsi.electrobazar.config.VerifactuProperties props;

    /* ── SUMMARY ──────────────────────────────────────────────── */

    private <T> Map<String, Long> kpis(List<T> list, StatusExtractor<T> fn, ReasonExtractor<T> reasonFn) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("total",             (long) list.size());
        m.put("accepted",          list.stream().filter(i -> fn.get(i) == AeatStatus.ACCEPTED || fn.get(i) == AeatStatus.ACCEPTED_WITH_ERRORS).count());
        m.put("pending",           list.stream().filter(i -> fn.get(i) == AeatStatus.PENDING_SEND).count());
        m.put("networkError",     list.stream().filter(i -> fn.get(i) == AeatStatus.REJECTED && reasonFn.get(i) == com.proconsi.electrobazar.model.AeatRejectionReason.NETWORK_ERROR).count());
        m.put("validationError",  list.stream().filter(i -> fn.get(i) == AeatStatus.REJECTED && reasonFn.get(i) == com.proconsi.electrobazar.model.AeatRejectionReason.VALIDATION_ERROR).count());
        m.put("annulled",          list.stream().filter(i -> fn.get(i) == AeatStatus.ANNULLED).count());
        return m;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        List<Invoice>              invoices  = invoiceRepository.findAll();
        List<RectificativeInvoice> rectifs   = rectificativeRepository.findAll();
        List<Ticket>               tickets   = ticketRepository.findAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoices",      kpis(invoices,  Invoice::getAeatStatus,              Invoice::getAeatRejectionReason));
        result.put("rectificativas",kpis(rectifs,   RectificativeInvoice::getAeatStatus, RectificativeInvoice::getAeatRejectionReason));
        result.put("tickets",       kpis(tickets,   Ticket::getAeatStatus,               Ticket::getAeatRejectionReason));
        return ResponseEntity.ok(result);
    }

    /* ── INVOICES ─────────────────────────────────────────────── */

    @GetMapping("/invoices")
    public ResponseEntity<?> invoices(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String reason,
            @RequestParam(required = false)    String start,
            @RequestParam(required = false)    String end,
            HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        List<Invoice> all = invoiceRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Invoice> filtered = filterRecords(all, Invoice::getAeatStatus, Invoice::getAeatRejectionReason, Invoice::getCreatedAt, status, reason, start, end);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Invoice inv : paginate(filtered, page, size)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",             inv.getId());
            row.put("number",         inv.getInvoiceNumber());
            row.put("type",           inv.getAeatStatus() == AeatStatus.ANNULLED ? "Anulación" : "F1");
            row.put("saleId",         inv.getSale() != null ? inv.getSale().getId() : null);
            row.put("amount",         inv.getSale() != null ? inv.getSale().getTotalAmount() : 0);
            row.put("createdAt",      inv.getCreatedAt() != null ? inv.getCreatedAt().toString() : null);
            row.put("aeatStatus",     inv.getAeatStatus() != null ? inv.getAeatStatus().name() : "NOT_SENT");
            row.put("submissionDate", inv.getAeatSubmissionDate() != null ? inv.getAeatSubmissionDate().toString() : null);
            row.put("retryCount",     inv.getAeatRetryCount());
            row.put("lastError",      inv.getAeatLastError());
            row.put("rejectionReason",inv.getAeatRejectionReason() != null ? inv.getAeatRejectionReason().name() : null);
            row.put("waitTime",       inv.getAeatWaitTime());
            row.put("hash",           inv.getHashCurrentInvoice());
            rows.add(row);
        }
        return ResponseEntity.ok(pageResponse(rows, filtered.size(), page, size));
    }

    /* ── RECTIFICATIVAS ───────────────────────────────────────── */

    @GetMapping("/rectificativas")
    public ResponseEntity<?> rectificativas(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String reason,
            @RequestParam(required = false)    String start,
            @RequestParam(required = false)    String end,
            HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        List<RectificativeInvoice> all = rectificativeRepository.findAllWithDetails(
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<RectificativeInvoice> filtered = filterRecords(all, RectificativeInvoice::getAeatStatus, RectificativeInvoice::getAeatRejectionReason, RectificativeInvoice::getCreatedAt, status, reason, start, end);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (RectificativeInvoice r : paginate(filtered, page, size)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",             r.getId());
            row.put("number",         r.getRectificativeNumber());
            boolean isR5 = r.getOriginalTicket() != null;
            row.put("type",           r.getAeatStatus() == AeatStatus.ANNULLED ? "Anulación" : (isR5 ? "R5" : "R4"));
            row.put("saleId",         r.getSaleReturn() != null && r.getSaleReturn().getOriginalSale() != null
                                        ? r.getSaleReturn().getOriginalSale().getId() : null);
            row.put("amount",         r.getSaleReturn() != null ? r.getSaleReturn().getTotalRefunded().negate() : 0);
            row.put("createdAt",      r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            row.put("aeatStatus",     r.getAeatStatus() != null ? r.getAeatStatus().name() : "NOT_SENT");
            row.put("submissionDate", r.getAeatSubmissionDate() != null ? r.getAeatSubmissionDate().toString() : null);
            row.put("retryCount",     r.getAeatRetryCount());
            row.put("lastError",      r.getAeatLastError());
            row.put("rejectionReason",r.getAeatRejectionReason() != null ? r.getAeatRejectionReason().name() : null);
            row.put("waitTime",       r.getAeatWaitTime());
            row.put("hash",           r.getHashCurrentInvoice());
            rows.add(row);
        }
        return ResponseEntity.ok(pageResponse(rows, filtered.size(), page, size));
    }

    /* ── TICKETS ──────────────────────────────────────────────── */

    @GetMapping("/tickets")
    public ResponseEntity<?> tickets(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String reason,
            @RequestParam(required = false)    String start,
            @RequestParam(required = false)    String end,
            HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        List<Ticket> all = ticketRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Ticket> filtered = filterRecords(all, Ticket::getAeatStatus, Ticket::getAeatRejectionReason, Ticket::getCreatedAt, status, reason, start, end);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Ticket t : paginate(filtered, page, size)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",             t.getId());
            row.put("number",         t.getTicketNumber());
            row.put("type",           t.getAeatStatus() == AeatStatus.ANNULLED ? "Anulación" : "F2");
            row.put("saleId",         t.getSale() != null ? t.getSale().getId() : null);
            row.put("amount",         t.getSale() != null ? t.getSale().getTotalAmount() : 0);
            row.put("createdAt",      t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            row.put("aeatStatus",     t.getAeatStatus() != null ? t.getAeatStatus().name() : "NOT_SENT");
            row.put("submissionDate", t.getAeatSubmissionDate() != null ? t.getAeatSubmissionDate().toString() : null);
            row.put("retryCount",     t.getAeatRetryCount());
            row.put("lastError",      t.getAeatLastError());
            row.put("rejectionReason",t.getAeatRejectionReason() != null ? t.getAeatRejectionReason().name() : null);
            row.put("waitTime",       t.getAeatWaitTime());
            row.put("hash",           t.getHashCurrentInvoice());
            rows.add(row);
        }
        return ResponseEntity.ok(pageResponse(rows, filtered.size(), page, size));
    }

    /* ── PENDING ──────────────────────────────────────────────── */

    @GetMapping("/pending")
    public ResponseEntity<?> pending(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        List<Map<String, Object>> list = new ArrayList<>();
        
        // Invoices
        invoiceRepository.findPendingSend().forEach(i -> list.add(mapPending(i.getId(), i.getInvoiceNumber(), "Factura", i.getAeatStatus(), i.getAeatSubmissionDate(), i.getAeatRetryCount(), i.getSale() != null ? i.getSale().getTotalAmount() : null)));
        
        // Tickets
        ticketRepository.findPendingSend().forEach(t -> list.add(mapPending(t.getId(), t.getTicketNumber(), "Ticket", t.getAeatStatus(), t.getAeatSubmissionDate(), t.getAeatRetryCount(), t.getSale() != null ? t.getSale().getTotalAmount() : null)));
        
        // Rectificativas
        rectificativeRepository.findPendingSend().forEach(r -> list.add(mapPending(r.getId(), r.getRectificativeNumber(), "Rectificativa", r.getAeatStatus(), r.getAeatSubmissionDate(), r.getAeatRetryCount(), r.getSaleReturn() != null ? r.getSaleReturn().getTotalRefunded().negate() : null)));

        // Sort by next retry (earliest first)
        list.sort((a, b) -> ((String) a.get("nextRetry")).compareTo((String) b.get("nextRetry")));

        return ResponseEntity.ok(list);
    }

    private Map<String, Object> mapPending(Long id, String number, String type, AeatStatus status, java.time.LocalDateTime lastSend, int retries, java.math.BigDecimal amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("number", number);
        m.put("type", type);
        m.put("status", status != null ? status.name() : "PENDING_SEND");
        m.put("lastSend", lastSend != null ? lastSend.toString() : null);
        m.put("retryCount", retries);
        m.put("amount", amount);

        // Next retry calculation
        java.time.LocalDateTime next;
        if (lastSend == null) {
            next = java.time.LocalDateTime.now();
        } else {
            long wait;
            if (retries <= 1) wait = 60;
            else if (retries == 2) wait = 120;
            else if (retries == 3) wait = 300;
            else if (retries == 4) wait = 900;
            else if (retries == 5) wait = 1800;
            else wait = 3600;
            next = lastSend.plusSeconds(wait);
        }
        m.put("nextRetry", next.toString());
        return m;
    }

    /* ── SETTINGS ─────────────────────────────────────────────── */

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("realEndpoint", props.getEnvironment().equalsIgnoreCase("pruebas") 
                ? "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"
                : "https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP");
        s.put("testEndpointUrl", props.getTestEndpointUrl());
        s.put("isFakeActive", props.getTestEndpointUrl() != null && !props.getTestEndpointUrl().isBlank());
        return ResponseEntity.ok(s);
    }

    @PostMapping("/settings/endpoint")
    public ResponseEntity<?> setEndpoint(@RequestBody Map<String, String> body, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        
        String url = body.get("url");
        props.setTestEndpointUrl(url);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{type}/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable String type, @PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        try {
            if ("invoices".equalsIgnoreCase(type)) verifactuService.submitInvoiceAsync(id);
            else if ("tickets".equalsIgnoreCase(type)) verifactuService.submitTicketAsync(id);
            else if ("rectificativas".equalsIgnoreCase(type)) verifactuService.submitRectificativeAsync(id);
            else return ResponseEntity.badRequest().body("Unknown type");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/subsanar/{type}/{id}")
    public ResponseEntity<?> subsanar(@PathVariable String type, @PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        try {
            verifactuService.submitSubsanacionAsync(id, type);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /* ── TEST ENDPOINTS ───────────────────────────────────────── */
    @GetMapping("/test/force-accepted-with-errors/{id}")
    public ResponseEntity<?> forceAcceptedWithErrors(@PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        return invoiceRepository.findById(id).map(inv -> {
            inv.setAeatStatus(AeatStatus.ACCEPTED_WITH_ERRORS);
            inv.setAeatLastError("AceptadoConErrores: El cálculo de la huella suministrada es incorrecta");
            inv.setAeatRejectionReason(null);
            invoiceRepository.save(inv);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /* ── HELPERS ──────────────────────────────────────────────── */

    @FunctionalInterface
    private interface StatusExtractor<T> { AeatStatus get(T item); }
    @FunctionalInterface
    private interface ReasonExtractor<T> { com.proconsi.electrobazar.model.AeatRejectionReason get(T item); }
    @FunctionalInterface
    private interface DateExtractor<T> { java.time.LocalDateTime get(T item); }

    private <T> List<T> filterRecords(List<T> list, StatusExtractor<T> statusFn, ReasonExtractor<T> reasonFn, DateExtractor<T> dateFn,
                                     String status, String reason, String start, String end) {
        java.util.stream.Stream<T> stream = list.stream();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            if ("NOT_SENT".equalsIgnoreCase(status)) stream = stream.filter(i -> statusFn.get(i) == null);
            else {
                AeatStatus s = AeatStatus.valueOf(status);
                stream = stream.filter(i -> s.equals(statusFn.get(i)));
            }
        }
        if (reason != null && !reason.isBlank() && !"ALL".equalsIgnoreCase(reason)) {
            com.proconsi.electrobazar.model.AeatRejectionReason r = com.proconsi.electrobazar.model.AeatRejectionReason.valueOf(reason);
            stream = stream.filter(i -> r.equals(reasonFn.get(i)));
        }
        if (start != null && !start.isBlank()) {
            java.time.LocalDateTime s = java.time.LocalDateTime.parse(start + "T00:00:00");
            stream = stream.filter(i -> dateFn.get(i) != null && !dateFn.get(i).isBefore(s));
        }
        if (end != null && !end.isBlank()) {
            java.time.LocalDateTime e = java.time.LocalDateTime.parse(end + "T23:59:59");
            stream = stream.filter(i -> dateFn.get(i) != null && !dateFn.get(i).isAfter(e));
        }
        return stream.toList();
    }

    private <T> List<T> paginate(List<T> list, int page, int size) {
        int start = Math.min(page * size, list.size());
        int end   = Math.min(start + size, list.size());
        return list.subList(start, end);
    }

    private Map<String, Object> pageResponse(List<Map<String, Object>> rows, int total, int page, int size) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content",       rows);
        m.put("totalElements", total);
        m.put("totalPages",    size > 0 ? (int) Math.ceil((double) total / size) : 1);
        m.put("page",          page);
        m.put("size",          size);
        return m;
    }
}
