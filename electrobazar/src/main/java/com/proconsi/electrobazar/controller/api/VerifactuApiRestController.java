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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.proconsi.electrobazar.model.Customer;
import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.AeatRejectionReason;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for VeriFactu / AEAT submission status dashboard.
 * Covers invoices (facturas), rectificative invoices (facturas rectificativas)
 * and simplified tickets (tickets).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/verifactu")
public class VerifactuApiRestController {

    private final com.proconsi.electrobazar.service.verifactu.VerifactuState verifactuState;
    private final com.proconsi.electrobazar.scheduler.VerifactuRetryScheduler retryScheduler;

    private final InvoiceRepository              invoiceRepository;
    private final RectificativeInvoiceRepository rectificativeRepository;
    private final TicketRepository               ticketRepository;
    private final com.proconsi.electrobazar.service.VerifactuService verifactuService;
    private final com.proconsi.electrobazar.service.InvoiceService invoiceService;
    private final com.proconsi.electrobazar.config.VerifactuProperties props;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GetMapping("/{type}/{id}/detail")
    public ResponseEntity<?> getDetail(@PathVariable String type, @PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        
        com.proconsi.electrobazar.model.Sale sale = null;
        if ("invoices".equalsIgnoreCase(type)) {
            sale = invoiceRepository.findById(id).map(Invoice::getSale).orElse(null);
        } else if ("tickets".equalsIgnoreCase(type)) {
            sale = ticketRepository.findById(id).map(Ticket::getSale).orElse(null);
        } else if ("rectificativas".equalsIgnoreCase(type) || "devoluciones".equalsIgnoreCase(type)) {
            sale = rectificativeRepository.findById(id).map(r -> r.getSaleReturn() != null ? r.getSaleReturn().getOriginalSale() : null).orElse(null);
        }

        if (sale == null) return ResponseEntity.notFound().build();

        Map<String, Object> resp = new java.util.HashMap<>();
        if (sale.getCustomer() != null) {
            com.proconsi.electrobazar.model.Customer c = sale.getCustomer();
            resp.put("customerName", c.getName());
            resp.put("customerTaxId", c.getTaxId());
            resp.put("customerAddress", c.getAddress());
            resp.put("customerPostalCode", c.getPostalCode());
            resp.put("customerCity", c.getCity());
        } else if (sale.getClientePuntualJson() != null) {
            try {
                Map<?, ?> map = objectMapper.readValue(sale.getClientePuntualJson(), Map.class);
                resp.put("customerName", map.get("nombreRazon"));
                resp.put("customerTaxId", map.get("nif"));
                resp.put("customerAddress", map.get("address"));
                resp.put("customerPostalCode", map.get("postalCode"));
                resp.put("customerCity", map.get("city"));
            } catch (Exception e) {
                // Ignore parse error
            }
        }
        return ResponseEntity.ok(resp);
    }

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

    /* ── ALL RECORDS ──────────────────────────────────────────── */

    @GetMapping("/all")
    public ResponseEntity<?> all(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String reason,
            @RequestParam(required = false)    String start,
            @RequestParam(required = false)    String end,
            HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        AeatStatus sEnum = (status != null && !"ALL".equalsIgnoreCase(status)) ? AeatStatus.valueOf(status) : null;
        AeatRejectionReason rEnum = (reason != null && !"ALL".equalsIgnoreCase(reason)) ? AeatRejectionReason.valueOf(reason) : null;
        LocalDateTime startDt = (start != null && !start.isBlank()) ? LocalDateTime.parse(start + "T00:00:00") : null;
        LocalDateTime endDt = (end != null && !end.isBlank()) ? LocalDateTime.parse(end + "T23:59:59") : null;

        // Optimization: Fetch top 20 of each instead of thousands
        PageRequest pr = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        List<Map<String, Object>> rows = new ArrayList<>();
        invoiceRepository.findByFilters(sEnum, rEnum, startDt, endDt, pr).getContent().forEach(i -> rows.add(mapInvoice(i)));
        ticketRepository.findByFilters(sEnum, rEnum, startDt, endDt, pr).getContent().forEach(t -> rows.add(mapTicket(t)));
        rectificativeRepository.findByFilters(sEnum, rEnum, startDt, endDt, pr).getContent().forEach(r -> rows.add(mapRectificative(r)));

        rows.sort((a, b) -> {
            String ca = (String) a.get("createdAt");
            String cb = (String) b.get("createdAt");
            if (ca == null) return (cb == null) ? 0 : 1;
            if (cb == null) return -1;
            return cb.compareTo(ca);
        });

        // Limit to 20 for 'All' view
        List<Map<String, Object>> result = rows.stream().limit(20).collect(Collectors.toList());
        return ResponseEntity.ok(pageResponse(result, result.size(), 0, 20));
    }

    /* ── INVOICES ─────────────────────────────────────────────── */

    @GetMapping("/invoices")
    public ResponseEntity<?> invoices(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String reason,
            @RequestParam(required = false)    String start,
            @RequestParam(required = false)    String end,
            HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        AeatStatus sEnum = (status != null && !"ALL".equalsIgnoreCase(status)) ? AeatStatus.valueOf(status) : null;
        AeatRejectionReason rEnum = (reason != null && !"ALL".equalsIgnoreCase(reason)) ? AeatRejectionReason.valueOf(reason) : null;
        LocalDateTime startDt = (start != null && !start.isBlank()) ? LocalDateTime.parse(start + "T00:00:00") : null;
        LocalDateTime endDt = (end != null && !end.isBlank()) ? LocalDateTime.parse(end + "T23:59:59") : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Invoice> p = invoiceRepository.findByFilters(sEnum, rEnum, startDt, endDt, pageable);

        List<Map<String, Object>> rows = p.getContent().stream().map(this::mapInvoice).toList();
        return ResponseEntity.ok(pageResponse(rows, (int) p.getTotalElements(), page, size));
    }

    /* ── RECTIFICATIVAS ───────────────────────────────────────── */

    @GetMapping("/rectificativas")
    public ResponseEntity<?> rectificativas(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String reason,
            @RequestParam(required = false)    String start,
            @RequestParam(required = false)    String end,
            HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        AeatStatus sEnum = (status != null && !"ALL".equalsIgnoreCase(status)) ? AeatStatus.valueOf(status) : null;
        AeatRejectionReason rEnum = (reason != null && !"ALL".equalsIgnoreCase(reason)) ? AeatRejectionReason.valueOf(reason) : null;
        LocalDateTime startDt = (start != null && !start.isBlank()) ? LocalDateTime.parse(start + "T00:00:00") : null;
        LocalDateTime endDt = (end != null && !end.isBlank()) ? LocalDateTime.parse(end + "T23:59:59") : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RectificativeInvoice> p = rectificativeRepository.findByFilters(sEnum, rEnum, startDt, endDt, pageable);

        List<Map<String, Object>> rows = p.getContent().stream().map(this::mapRectificative).toList();
        return ResponseEntity.ok(pageResponse(rows, (int) p.getTotalElements(), page, size));
    }

    /* ── TICKETS ──────────────────────────────────────────────── */

    @GetMapping("/tickets")
    public ResponseEntity<?> tickets(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String reason,
            @RequestParam(required = false)    String start,
            @RequestParam(required = false)    String end,
            HttpSession session) {

        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        AeatStatus sEnum = (status != null && !"ALL".equalsIgnoreCase(status)) ? AeatStatus.valueOf(status) : null;
        AeatRejectionReason rEnum = (reason != null && !"ALL".equalsIgnoreCase(reason)) ? AeatRejectionReason.valueOf(reason) : null;
        LocalDateTime startDt = (start != null && !start.isBlank()) ? LocalDateTime.parse(start + "T00:00:00") : null;
        LocalDateTime endDt = (end != null && !end.isBlank()) ? LocalDateTime.parse(end + "T23:59:59") : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Ticket> p = ticketRepository.findByFilters(sEnum, rEnum, startDt, endDt, pageable);

        List<Map<String, Object>> rows = p.getContent().stream().map(this::mapTicket).toList();
        return ResponseEntity.ok(pageResponse(rows, (int) p.getTotalElements(), page, size));
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
            else if ("rectificativas".equalsIgnoreCase(type) || "devoluciones".equalsIgnoreCase(type)) verifactuService.submitRectificativeAsync(id);
            else return ResponseEntity.badRequest().body("Unknown type");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PutMapping("/edit/{type}/{id}")
    public ResponseEntity<?> editSubsanacion(@PathVariable String type, @PathVariable Long id, @RequestBody com.proconsi.electrobazar.dto.SubsanarRequest data, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        try {
            verifactuService.prepararSubsanacion(id, type, data);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/subsanar/{type}/{id}")
    public ResponseEntity<?> subsanar(
            @PathVariable String type, 
            @PathVariable Long id, 
            @RequestBody(required = false) com.proconsi.electrobazar.dto.SubsanarRequest data,
            HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        try {
            if (data != null) {
                verifactuService.submitSubsanacionWithCorrection(id, type, data);
            } else {
                verifactuService.submitSubsanacionAsync(id, type);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @GetMapping("/cooldown")
    public ResponseEntity<?> getCooldown() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("lastSendTime", verifactuState.getLastSendTime());
        resp.put("waitSeconds", verifactuState.getCurrentWaitSeconds());
        resp.put("remainingSeconds", verifactuState.getRemainingSeconds());
        resp.put("readyToSend", !verifactuState.isCooldownActive());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/scheduler-status")
    public ResponseEntity<?> schedulerStatus() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("schedulerBeanAlive", true);
        resp.put("verifactuEnabled", props.isEnabled());
        resp.put("cooldownActive", verifactuState.isCooldownActive());
        resp.put("remainingSeconds", verifactuState.getRemainingSeconds());
        resp.put("lastSendTime", verifactuState.getLastSendTime());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/scheduler-status/force-tick")
    public ResponseEntity<?> forceTick(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();
        try {
            retryScheduler.retryPending();
            return ResponseEntity.ok(Map.of("triggered", true, "message", "Tick forzado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{type}/{id}/qr")
    public ResponseEntity<?> getQr(@PathVariable String type, @PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        String qrBase64 = null;
        String qrUrl = null;

        if ("invoices".equalsIgnoreCase(type)) {
            Invoice inv = invoiceRepository.findById(id).orElse(null);
            if (inv != null) {
                qrBase64 = invoiceService.generateQrCodeBase64(inv);
                qrUrl = invoiceService.generateQrUrl(inv);
            }
        } else if ("tickets".equalsIgnoreCase(type)) {
            Ticket tick = ticketRepository.findById(id).orElse(null);
            if (tick != null) {
                qrBase64 = invoiceService.generateQrCodeBase64(tick);
                qrUrl = invoiceService.generateQrUrl(tick);
            }
        } else if ("rectificativas".equalsIgnoreCase(type) || "devoluciones".equalsIgnoreCase(type)) {
            RectificativeInvoice rect = rectificativeRepository.findById(id).orElse(null);
            if (rect != null) {
                qrBase64 = invoiceService.generateQrCodeBase64(rect);
                qrUrl = invoiceService.generateQrUrl(rect);
            }
        }

        if (qrBase64 == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of("qrBase64", qrBase64, "qrUrl", qrUrl));
    }

    @GetMapping("/response/{type}/{id}")
    public ResponseEntity<?> getResponse(@PathVariable String type, @PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        String raw = null;
        String csv = null;
        String status = null;
        String lastError = null;

        if ("invoices".equalsIgnoreCase(type)) {
            Invoice inv = invoiceRepository.findById(id).orElse(null);
            if (inv != null) {
                raw = inv.getAeatRawResponse();
                csv = inv.getAeatCsv();
                status = inv.getAeatStatus() != null ? inv.getAeatStatus().name() : null;
                lastError = inv.getAeatLastError();
            }
        } else if ("tickets".equalsIgnoreCase(type)) {
            Ticket tick = ticketRepository.findById(id).orElse(null);
            if (tick != null) {
                raw = tick.getAeatRawResponse();
                csv = tick.getAeatCsv();
                status = tick.getAeatStatus() != null ? tick.getAeatStatus().name() : null;
                lastError = tick.getAeatLastError();
            }
        } else if ("rectificativas".equalsIgnoreCase(type) || "devoluciones".equalsIgnoreCase(type)) {
            RectificativeInvoice rect = rectificativeRepository.findById(id).orElse(null);
            if (rect != null) {
                raw = rect.getAeatRawResponse();
                csv = rect.getAeatCsv();
                status = rect.getAeatStatus() != null ? rect.getAeatStatus().name() : null;
                lastError = rect.getAeatLastError();
            }
        }

        if (raw == null && csv == null) return ResponseEntity.notFound().build();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("csv", csv);
        resp.put("rawResponse", raw);
        resp.put("estadoRegistro", status);
        resp.put("errorCodigo", null);
        resp.put("errorDescripcion", lastError);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/details/{type}/{id}")
    public ResponseEntity<?> getDetails(@PathVariable String type, @PathVariable Long id, HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute("admin")))
            return ResponseEntity.status(401).build();

        Map<String, Object> resp = new LinkedHashMap<>();
        
        if ("invoices".equalsIgnoreCase(type)) {
            Invoice inv = invoiceRepository.findById(id).orElse(null);
            if (inv != null) {
                fillDetails(resp, inv.getSale(), inv.getInvoiceNumber(), inv.getCreatedAt(), inv.getAeatStatus(), 
                            inv.getAeatXmlSent(), inv.getAeatRawResponse());
            }
        } else if ("tickets".equalsIgnoreCase(type)) {
            Ticket tick = ticketRepository.findById(id).orElse(null);
            if (tick != null) {
                fillDetails(resp, tick.getSale(), tick.getTicketNumber(), tick.getCreatedAt(), tick.getAeatStatus(),
                            tick.getAeatXmlSent(), tick.getAeatRawResponse());
            }
        } else if ("rectificativas".equalsIgnoreCase(type) || "devoluciones".equalsIgnoreCase(type)) {
            RectificativeInvoice rect = rectificativeRepository.findById(id).orElse(null);
            if (rect != null) {
                fillDetails(resp, rect.getSaleReturn().getOriginalSale(), rect.getRectificativeNumber(), rect.getCreatedAt(), rect.getAeatStatus(),
                            rect.getAeatXmlSent(), rect.getAeatRawResponse());
            }
        }

        if (resp.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(resp);
    }

    private void fillDetails(Map<String, Object> resp, Sale sale, String number, LocalDateTime date, AeatStatus status, String sent, String received) {
        Map<String, Object> client = new LinkedHashMap<>();
        if (sale != null && sale.getCustomer() != null) {
            Customer c = sale.getCustomer();
            client.put("nombre", c.getName());
            client.put("nif", c.getTaxId());
            client.put("direccion", c.getAddress());
            client.put("cp", c.getPostalCode());
        } else {
            client.put("nombre", "Cliente Final / Simplificada");
            client.put("nif", "—");
        }
        resp.put("cliente", client);

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("numero", number);
        fact.put("fecha", date);
        fact.put("importe", sale != null ? sale.getTotalAmount() : 0);
        fact.put("estadoAeat", status != null ? status.name() : "PENDIENTE");
        resp.put("factura", fact);

        resp.put("verifactuXmlSent", sent);
        resp.put("verifactuXmlReceived", received);
    }

    /* ── HELPERS ──────────────────────────────────────────────── */
    
    private Map<String, Object> mapInvoice(Invoice inv) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id",             inv.getId());
        row.put("number",         inv.getInvoiceNumber());
        row.put("type",           inv.getAeatStatus() == AeatStatus.ANNULLED ? "Anulación" : "F1");
        row.put("realType",       "invoices");
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
        // QR generation removed for list view performance
        row.put("qrUrl",          invoiceService.generateQrUrl(inv));
        return row;
    }

    private Map<String, Object> mapTicket(Ticket t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id",             t.getId());
        row.put("number",         t.getTicketNumber());
        row.put("type",           t.getAeatStatus() == AeatStatus.ANNULLED ? "Anulación" : "F2");
        row.put("realType",       "tickets");
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
        // QR generation removed for list view performance
        row.put("qrUrl",          invoiceService.generateQrUrl(t));
        return row;
    }

    private Map<String, Object> mapRectificative(RectificativeInvoice r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id",             r.getId());
        row.put("number",         r.getRectificativeNumber());
        boolean isR5 = r.getOriginalTicket() != null;
        row.put("type",           r.getAeatStatus() == AeatStatus.ANNULLED ? "Anulación" : (isR5 ? "R5" : "R4"));
        row.put("realType",       isR5 ? "devoluciones" : "rectificativas");
        row.put("saleId",         r.getSaleReturn() != null && r.getSaleReturn().getOriginalSale() != null
                                    ? r.getSaleReturn().getOriginalSale().getId() : null);
        row.put("amount",         r.getSaleReturn() != null ? r.getSaleReturn().getTotalRefunded().negate() : 0);
        row.put("createdAt",      r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        row.put("returnNumber",   r.getSaleReturn() != null ? r.getSaleReturn().getReturnNumber() : null);
        row.put("aeatStatus",     r.getAeatStatus() != null ? r.getAeatStatus().name() : "NOT_SENT");
        row.put("submissionDate", r.getAeatSubmissionDate() != null ? r.getAeatSubmissionDate().toString() : null);
        row.put("retryCount",     r.getAeatRetryCount());
        row.put("lastError",      r.getAeatLastError());
        row.put("rejectionReason",r.getAeatRejectionReason() != null ? r.getAeatRejectionReason().name() : null);
        row.put("waitTime",       r.getAeatWaitTime());
        row.put("returnId",       r.getSaleReturn() != null ? r.getSaleReturn().getId() : null);
        row.put("hash",           r.getHashCurrentInvoice());
        // QR generation removed for list view performance
        row.put("qrUrl",          invoiceService.generateQrUrl(r));
        return row;
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

    @FunctionalInterface
    private interface StatusExtractor<T> {
        AeatStatus get(T item);
    }

    @FunctionalInterface
    private interface ReasonExtractor<T> {
        AeatRejectionReason get(T item);
    }
}

