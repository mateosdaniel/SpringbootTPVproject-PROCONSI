package com.proconsi.electrobazar.service.impl;

import com.proconsi.electrobazar.config.VerifactuProperties;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.CompanySettingsRepository;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.RectificativeInvoiceRepository;
import com.proconsi.electrobazar.repository.TicketRepository;
import com.proconsi.electrobazar.service.VerifactuService;
import com.proconsi.electrobazar.service.verifactu.VerifactuSoapClient;
import com.proconsi.electrobazar.service.verifactu.VerifactuXmlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.ApplicationEventPublisher;
import com.proconsi.electrobazar.util.VerifactuHashCalculator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proconsi.electrobazar.dto.SubsanarRequest;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.repository.SaleRepository;

import java.time.LocalDateTime;

/**
 * Implementación de VeriFactu.
 * Los métodos submitXxxAsync son @Async: se ejecutan en un hilo separado
 * para no bloquear la transacción de creación de la factura/ticket.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerifactuServiceImpl implements VerifactuService {

    private final VerifactuProperties props;
    private final VerifactuXmlBuilder xmlBuilder;
    private final VerifactuSoapClient soapClient;
    private final VerifactuHashCalculator hashCalculator;

    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;
    private final RectificativeInvoiceRepository rectRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final com.proconsi.electrobazar.service.verifactu.VerifactuValidator validator;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final com.proconsi.electrobazar.service.verifactu.VerifactuState verifactuState;
    
    @Autowired
    private ObjectMapper objectMapper;

    // ================================================================
    //  Submit async methods
    // ================================================================

    @Override
    @Async
    @Transactional
    public void submitInvoiceAsync(Long invoiceId) {
        if (!props.isEnabled()) return;
        if (verifactuState.isCooldownActive()) {
            log.debug("Verifactu: cooldown active, deferring invoice {} to scheduler", invoiceId);
            return;
        }
        invoiceRepository.findById(invoiceId).ifPresent(this::submitInvoice);
    }

    @Override
    @Async
    @Transactional
    public void submitTicketAsync(Long ticketId) {
        if (!props.isEnabled()) return;
        if (verifactuState.isCooldownActive()) {
            log.debug("Verifactu: cooldown active, deferring ticket {} to scheduler", ticketId);
            return;
        }
        ticketRepository.findById(ticketId).ifPresent(this::submitTicket);
    }

    @Override
    @Async
    @Transactional
    public void submitRectificativeAsync(Long rectId) {
        if (!props.isEnabled()) return;
        if (verifactuState.isCooldownActive()) {
            log.debug("Verifactu: cooldown active, deferring rectificative {} to scheduler", rectId);
            return;
        }
        rectRepository.findById(rectId).ifPresent(this::submitRectificative);
    }

    @Override
    @Async
    @Transactional
    public void submitAnulacionAsync(Long invoiceId, boolean isTicket) {
        if (!props.isEnabled()) return;
        if (verifactuState.isCooldownActive()) {
            log.debug("Verifactu: cooldown active, deferring anulacion to scheduler");
            return;
        }
        if (isTicket) {
            ticketRepository.findById(invoiceId).ifPresent(this::submitAnulacionTicket);
        } else {
            invoiceRepository.findById(invoiceId).ifPresent(this::submitAnulacionInvoice);
        }
    }

    @Override
    @Async
    @Transactional
    public void submitSubsanacionAsync(Long id, String type) {
        if (!props.isEnabled()) return;
        if (verifactuState.isCooldownActive()) {
            log.debug("Verifactu: cooldown active, deferring subsanacion to scheduler");
            return;
        }
        if ("invoices".equalsIgnoreCase(type)) invoiceRepository.findById(id).ifPresent(this::submitSubsanacionInvoice);
        else if ("tickets".equalsIgnoreCase(type)) ticketRepository.findById(id).ifPresent(this::submitSubsanacionTicket);
        else if ("rectificativas".equalsIgnoreCase(type)) rectRepository.findById(id).ifPresent(this::submitSubsanacionRectificative);
    }

    @Override
    @Transactional
    public void submitSubsanacionWithCorrection(Long id, String type, SubsanarRequest data) {
        if (!props.isEnabled()) return;

        Sale sale = null;
        if ("invoices".equalsIgnoreCase(type)) {
            Invoice inv = invoiceRepository.findById(id).orElse(null);
            if (inv != null) sale = inv.getSale();
        } else if ("tickets".equalsIgnoreCase(type)) {
            Ticket t = ticketRepository.findById(id).orElse(null);
            if (t != null) sale = t.getSale();
        } else if ("rectificativas".equalsIgnoreCase(type)) {
            RectificativeInvoice r = rectRepository.findById(id).orElse(null);
            if (r != null && r.getSaleReturn() != null) sale = r.getSaleReturn().getOriginalSale();
        }

        if (sale == null) return;

        if (sale.getCustomer() != null) {
            Customer c = sale.getCustomer();
            c.setName(data.getNombreRazon());
            c.setTaxId(data.getNif());
            c.setAddress(data.getAddress());
            c.setPostalCode(data.getPostalCode());
            c.setCity(data.getCity());
            customerRepository.save(c);
        } else {
            try {
                String json = objectMapper.writeValueAsString(data);
                sale.setClientePuntualJson(json);
                saleRepository.save(sale);
            } catch (Exception e) {
                log.error("Error updating puntual customer JSON: {}", e.getMessage());
            }
        }

        // Now trigger subsanacion
        submitSubsanacionAsync(id, type);
    }

    // ================================================================
    //  Retry (llamado desde el scheduler)
    // ================================================================

    @Override
    @Transactional
    public void retryPendingSend() {
        if (!props.isEnabled()) return;

        // 1. Recolectar registros pendientes (Facturas, Tickets, Rectificativas)
        java.util.List<Object> allPending = new java.util.ArrayList<>();
        
        invoiceRepository.findPendingSend().stream()
                .filter(this::isEligibleForAutoRetry)
                .forEach(allPending::add);

        ticketRepository.findPendingSend().stream()
                .filter(this::isEligibleForAutoRetryTicket)
                .forEach(allPending::add);

        rectRepository.findPendingSend().stream()
                .filter(this::isEligibleForAutoRetryRect)
                .forEach(allPending::add);

        if (allPending.isEmpty()) return;

        int count = allPending.size();

        // REGLAS VERI*FACTU DE ENVÍO POR LOTES:
        
        // CONDICIÓN A: Hay 1.000 o más registros. Se envían 1.000 inmediatamente ignorando el tiempo de espera.
        if (count >= 1000) {
            log.info("Verifactu: Condición A cumplida ({} registros). Enviando lote de 1.000 INMEDIATAMENTE.", count);
            sendBatch(allPending.subList(0, 1000));
            return;
        }

        // CONDICIÓN B: Menos de 1.000 registros, pero ha transcurrido el tiempo de espera estipulado.
        if (verifactuState.isCooldownActive()) {
            log.debug("Verifactu: Condición B no cumplida. Cooldown activo (faltan {}s).", 
                verifactuState.getRemainingSeconds());
            return;
        }

        log.info("Verifactu: Condición B cumplida ({} registros, tiempo de espera agotado). Enviando lote parcial.", count);
        sendBatch(allPending);
    }

    private void sendBatch(java.util.List<Object> records) {
        CompanySettings company = getCompany();
        try {
            String xml = xmlBuilder.buildBatch(records, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando BATCH de {} registros a AEAT", records.size());
            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
            
            processBatchResponse(records, resp);
            verifactuState.setLastSendTime(java.time.LocalDateTime.now());
            if (resp.waitTime() > 0) {
                verifactuState.setCurrentWaitSeconds(resp.waitTime());
            }
        } catch (Exception e) {
            log.error("Verifactu: error en envío batch: {}", e.getMessage(), e);
            // Mark all as network error for retry
            for (Object rec : records) {
                if (rec instanceof Invoice inv) markError(inv, e.getMessage());
                else if (rec instanceof Ticket t) markErrorTicket(t, e.getMessage());
                else if (rec instanceof RectificativeInvoice r) markErrorRect(r, e.getMessage());
            }
        }
    }

    private void processBatchResponse(java.util.List<Object> records, VerifactuSoapClient.AeatBatchResponse resp) {
        String csv = resp.csv(); // CSV Global del envío
        log.info("Verifactu: procesando respuesta batch. Estado global: {}. CSV: {}", resp.estadoEnvio(), csv);

        for (Object rec : records) {
            String numSerie = getRecordNumSerie(rec);
            String fechaExp = getRecordFechaExp(rec);

            // Iterar OBLIGATORIAMENTE sobre la lista de nodos RespuestaLinea
            VerifactuSoapClient.AeatLineResponse lineResp = resp.lines().stream()
                    .filter(l -> l.numSerie().equals(numSerie) && l.fechaExp().equals(fechaExp))
                    .findFirst()
                    .orElse(null);

            if (lineResp != null) {
                // Actualizar estado individual de cada factura usando su EstadoRegistro
                updateRecordStatus(rec, lineResp, csv, resp.rawResponse(), resp.waitTime());
            } else {
                log.warn("Verifactu: registro {} no encontrado en respuesta batch de AEAT", numSerie);
            }
        }
    }

    private String getRecordNumSerie(Object rec) {
        if (rec instanceof Invoice i) return i.getInvoiceNumber();
        if (rec instanceof Ticket t) return t.getTicketNumber();
        if (rec instanceof RectificativeInvoice r) return r.getRectificativeNumber();
        return "";
    }

    private String getRecordFechaExp(Object rec) {
        LocalDateTime dt = null;
        if (rec instanceof Invoice i) dt = i.getCreatedAt();
        if (rec instanceof Ticket t) dt = t.getCreatedAt();
        if (rec instanceof RectificativeInvoice r) dt = r.getCreatedAt();
        return dt != null ? hashCalculator.getFechaExpedicion(dt) : "";
    }

    private void updateRecordStatus(Object rec, VerifactuSoapClient.AeatLineResponse line, String csv, String raw, Integer wait) {
        if (rec instanceof Invoice i) {
            i.setAeatCsv(csv);
            updateInvoiceStatusFromLine(i, line, raw, wait);
        } else if (rec instanceof Ticket t) {
            t.setAeatCsv(csv);
            updateTicketStatusFromLine(t, line, raw, wait);
        } else if (rec instanceof RectificativeInvoice r) {
            r.setAeatCsv(csv);
            updateRectStatusFromLine(r, line, raw, wait);
        }

        // Global cooldown update (Feature 6)
        verifactuState.setLastSendTime(LocalDateTime.now());
        if (wait != null && wait > 0) {
            verifactuState.setCurrentWaitSeconds(wait);
        }
    }

    private void updateInvoiceStatusFromLine(Invoice invoice, VerifactuSoapClient.AeatLineResponse line, String raw, Integer wait) {
        invoice.setAeatSubmissionDate(LocalDateTime.now());
        invoice.setAeatRawResponse(raw);
        invoice.setAeatWaitTime(wait);
        
        boolean ok = "Correcto".equalsIgnoreCase(line.estadoRegistro()) || "AceptadaConErrores".equalsIgnoreCase(line.estadoRegistro());
        // Handle Duplicate (Feature 5)
        if (!ok) {
            boolean isDup = "Correcto".equalsIgnoreCase(line.estadoDuplicado()) || 
                           "3000".equals(line.codError()) || 
                           (line.descError() != null && line.descError().toLowerCase().contains("duplicado"));
            if (isDup) {
                ok = true;
                invoice.setAeatLastError("Registro ya existente en AEAT");
                log.info("Verifactu: factura {} detectada como duplicada aceptada en AEAT", invoice.getInvoiceNumber());
            }
        }

        if (ok) {
            invoice.setAeatStatus("AceptadaConErrores".equalsIgnoreCase(line.estadoRegistro()) ? AeatStatus.ACCEPTED_WITH_ERRORS : AeatStatus.ACCEPTED);
            invoice.setAeatLastError(line.descError());
            invoice.setAeatRejectionReason(null);
        } else {
            invoice.setAeatStatus(AeatStatus.REJECTED);
            invoice.setAeatLastError(line.codError() + ": " + line.descError());
            invoice.setAeatRetryCount(invoice.getAeatRetryCount() + 1);
            invoice.setAeatRejectionReason(classifyRejection(line.codError(), line.descError()));
        }
        invoiceRepository.save(invoice);
    }

    private void updateTicketStatusFromLine(Ticket ticket, VerifactuSoapClient.AeatLineResponse line, String raw, Integer wait) {
        ticket.setAeatSubmissionDate(LocalDateTime.now());
        ticket.setAeatRawResponse(raw);
        ticket.setAeatWaitTime(wait);

        boolean ok = "Correcto".equalsIgnoreCase(line.estadoRegistro()) || "AceptadaConErrores".equalsIgnoreCase(line.estadoRegistro());
        if (!ok) {
            boolean isDup = "Correcto".equalsIgnoreCase(line.estadoDuplicado()) || 
                           "3000".equals(line.codError()) || 
                           (line.descError() != null && line.descError().toLowerCase().contains("duplicado"));
            if (isDup) {
                ok = true;
                ticket.setAeatLastError("Registro ya existente en AEAT");
            }
        }

        if (ok) {
            ticket.setAeatStatus("AceptadaConErrores".equalsIgnoreCase(line.estadoRegistro()) ? AeatStatus.ACCEPTED_WITH_ERRORS : AeatStatus.ACCEPTED);
            ticket.setAeatLastError(line.descError());
            ticket.setAeatRejectionReason(null);
        } else {
            ticket.setAeatStatus(AeatStatus.REJECTED);
            ticket.setAeatLastError(line.codError() + ": " + line.descError());
            ticket.setAeatRetryCount(ticket.getAeatRetryCount() + 1);
            ticket.setAeatRejectionReason(classifyRejection(line.codError(), line.descError()));
        }
        ticketRepository.save(ticket);
    }

    private void updateRectStatusFromLine(RectificativeInvoice rect, VerifactuSoapClient.AeatLineResponse line, String raw, Integer wait) {
        rect.setAeatSubmissionDate(LocalDateTime.now());
        rect.setAeatRawResponse(raw);
        rect.setAeatWaitTime(wait);

        boolean ok = "Correcto".equalsIgnoreCase(line.estadoRegistro()) || "AceptadaConErrores".equalsIgnoreCase(line.estadoRegistro());
        if (!ok) {
            boolean isDup = "Correcto".equalsIgnoreCase(line.estadoDuplicado()) || 
                           "3000".equals(line.codError()) || 
                           (line.descError() != null && line.descError().toLowerCase().contains("duplicado"));
            if (isDup) {
                ok = true;
                rect.setAeatLastError("Registro ya existente en AEAT");
            }
        }

        if (ok) {
            rect.setAeatStatus("AceptadaConErrores".equalsIgnoreCase(line.estadoRegistro()) ? AeatStatus.ACCEPTED_WITH_ERRORS : AeatStatus.ACCEPTED);
            rect.setAeatLastError(line.descError());
            rect.setAeatRejectionReason(null);
        } else {
            rect.setAeatStatus(AeatStatus.REJECTED);
            rect.setAeatLastError(line.codError() + ": " + line.descError());
            rect.setAeatRetryCount(rect.getAeatRetryCount() + 1);
            rect.setAeatRejectionReason(classifyRejection(line.codError(), line.descError()));
        }
        rectRepository.save(rect);
    }

    private boolean isEligibleForAutoRetry(Invoice i) {
        // Only auto-retry if it's a network error or hasn't been sent yet.
        // Validation errors require manual intervention.
        boolean canAuto = i.getAeatRejectionReason() == null || i.getAeatRejectionReason() == AeatRejectionReason.NETWORK_ERROR;
        return canAuto && isReadyForRetry(i.getAeatSubmissionDate(), i.getAeatRetryCount(), i.getAeatRejectionReason());
    }

    private boolean isEligibleForAutoRetryTicket(Ticket t) {
        boolean canAuto = t.getAeatRejectionReason() == null || t.getAeatRejectionReason() == AeatRejectionReason.NETWORK_ERROR;
        return canAuto && isReadyForRetry(t.getAeatSubmissionDate(), t.getAeatRetryCount(), t.getAeatRejectionReason());
    }

    private boolean isEligibleForAutoRetryRect(RectificativeInvoice r) {
        boolean canAuto = r.getAeatRejectionReason() == null || r.getAeatRejectionReason() == AeatRejectionReason.NETWORK_ERROR;
        return canAuto && isReadyForRetry(r.getAeatSubmissionDate(), r.getAeatRetryCount(), r.getAeatRejectionReason());
    }

    // ================================================================
    //  Lógica de envío
    // ================================================================

    private void submitInvoice(Invoice invoice) {
        CompanySettings company = getCompany();
        try {
            // Pre-send validation
            validator.validateInvoice(invoice, company);

            String xml = xmlBuilder.buildAltaInvoice(invoice, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando factura {} a AEAT [entorno: {}]",
                    invoice.getInvoiceNumber(), props.getEnvironment());
            log.debug("Verifactu XML: \n{}", xml);

            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
            if (resp.lines().size() > 0) {
                updateRecordStatus(invoice, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
            } else {
                // Handle global error
                markError(invoice, resp.estadoEnvio());
            }
        } catch (com.proconsi.electrobazar.service.verifactu.VerifactuValidator.ValidationException ve) {
            log.warn("Verifactu: fallo validación prevuelo factura {}: {}", invoice.getInvoiceNumber(), ve.getMessage());
            markValidationError(invoice, ve.getMessage());
        } catch (Exception e) {
            log.error("Verifactu: error procesando factura {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            markError(invoice, e.getMessage());
        }
    }

    private void submitTicket(Ticket ticket) {
        CompanySettings company = getCompany();
        try {
            // Pre-send validation
            validator.validateTicket(ticket, company);

            String xml = xmlBuilder.buildAltaTicket(ticket, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando ticket {} a AEAT [entorno: {}]",
                    ticket.getTicketNumber(), props.getEnvironment());
            log.debug("Verifactu XML: \n{}", xml);

            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
            if (resp.lines().size() > 0) {
                updateRecordStatus(ticket, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
            } else {
                markErrorTicket(ticket, resp.estadoEnvio());
            }
        } catch (com.proconsi.electrobazar.service.verifactu.VerifactuValidator.ValidationException ve) {
            log.warn("Verifactu: fallo validación prevuelo ticket {}: {}", ticket.getTicketNumber(), ve.getMessage());
            markValidationErrorTicket(ticket, ve.getMessage());
        } catch (Exception e) {
            log.error("Verifactu: error procesando ticket {}: {}", ticket.getTicketNumber(), e.getMessage(), e);
            markErrorTicket(ticket, e.getMessage());
        }
    }

    private void submitRectificative(RectificativeInvoice rect) {
        CompanySettings company = getCompany();
        try {
            // Pre-send validation
            validator.validateRectificative(rect, company);

            String xml = xmlBuilder.buildAltaRectificative(rect, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando rectificativa {} a AEAT [entorno: {}]",
                    rect.getRectificativeNumber(), props.getEnvironment());
            log.debug("Verifactu XML: \n{}", xml);

            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
            if (resp.lines().size() > 0) {
                updateRecordStatus(rect, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
            } else {
                markErrorRect(rect, resp.estadoEnvio());
            }
        } catch (com.proconsi.electrobazar.service.verifactu.VerifactuValidator.ValidationException ve) {
            log.warn("Verifactu: fallo validación prevuelo rectificativa {}: {}", rect.getRectificativeNumber(), ve.getMessage());
            markValidationErrorRect(rect, ve.getMessage());
        } catch (Exception e) {
            log.error("Verifactu: error procesando rectificativa {}: {}", rect.getRectificativeNumber(), e.getMessage(), e);
            markErrorRect(rect, e.getMessage());
        }
    }

    private void submitAnulacionInvoice(Invoice invoice) {
        CompanySettings company = getCompany();
        try {
            String xml = xmlBuilder.buildAnulacionInvoice(invoice, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando ANULACIÓN de factura {} a AEAT", invoice.getInvoiceNumber());

            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
             if (resp.lines().size() > 0) {
                updateRecordStatus(invoice, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
                // Override status if it was an annulment success
                if ("Correcto".equalsIgnoreCase(resp.lines().get(0).estadoRegistro())) {
                    invoice.setAeatStatus(AeatStatus.ANNULLED);
                    invoiceRepository.save(invoice);
                }
            }
        } catch (Exception e) {
            log.error("Verifactu: error anulando factura {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            markError(invoice, "Error anulación: " + e.getMessage());
        }
    }

    private void submitAnulacionTicket(Ticket ticket) {
        CompanySettings company = getCompany();
        try {
            String xml = xmlBuilder.buildAnulacionTicket(ticket, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando ANULACIÓN de ticket {} a AEAT", ticket.getTicketNumber());

            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
             if (resp.lines().size() > 0) {
                updateRecordStatus(ticket, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
                if ("Correcto".equalsIgnoreCase(resp.lines().get(0).estadoRegistro())) {
                    ticket.setAeatStatus(AeatStatus.ANNULLED);
                    ticketRepository.save(ticket);
                }
            }
        } catch (Exception e) {
            log.error("Verifactu: error anulando ticket {}: {}", ticket.getTicketNumber(), e.getMessage());
            markErrorTicket(ticket, "Error anulación: " + e.getMessage());
        }
    }

    private void submitSubsanacionInvoice(Invoice invoice) {
        CompanySettings company = getCompany();
        try {
            validator.validateInvoice(invoice, company);
            String xml = xmlBuilder.buildSubsanacionInvoice(invoice, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());
            log.info("Verifactu: enviando SUBSANACIÓN de factura {} a AEAT", invoice.getInvoiceNumber());
            log.debug("Subsanacion XML: \n{}", xml);
            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
            if (resp.lines().size() > 0) {
                updateRecordStatus(invoice, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
            } else {
                // Handle global error
                markError(invoice, resp.estadoEnvio());
            }
        } catch (Exception e) {
            log.error("Verifactu: error subsanando factura {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            markError(invoice, "Error subsanación: " + e.getMessage());
        }
    }

    private void submitSubsanacionTicket(Ticket ticket) {
        CompanySettings company = getCompany();
        try {
            validator.validateTicket(ticket, company);
            String xml = xmlBuilder.buildSubsanacionTicket(ticket, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());
            log.info("Verifactu: enviando SUBSANACIÓN de ticket {} a AEAT", ticket.getTicketNumber());
            log.debug("Subsanacion XML: \n{}", xml);
            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
            if (resp.lines().size() > 0) {
                updateRecordStatus(ticket, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
            } else {
                markErrorTicket(ticket, resp.estadoEnvio());
            }
        } catch (Exception e) {
            log.error("Verifactu: error subsanando ticket {}: {}", ticket.getTicketNumber(), e.getMessage());
            markErrorTicket(ticket, "Error subsanación: " + e.getMessage());
        }
    }

    private void submitSubsanacionRectificative(RectificativeInvoice rect) {
        CompanySettings company = getCompany();
        try {
            validator.validateRectificative(rect, company);
            String xml = xmlBuilder.buildSubsanacionRectificative(rect, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());
            log.info("Verifactu: enviando SUBSANACIÓN de rectificativa {} a AEAT", rect.getRectificativeNumber());
            log.debug("Subsanacion XML: \n{}", xml);
            VerifactuSoapClient.AeatBatchResponse resp = soapClient.send(xml);
            if (resp.lines().size() > 0) {
                updateRecordStatus(rect, resp.lines().get(0), resp.csv(), resp.rawResponse(), resp.waitTime());
            } else {
                markErrorRect(rect, resp.estadoEnvio());
            }
        } catch (Exception e) {
            log.error("Verifactu: error subsanando rectificativa {}: {}", rect.getRectificativeNumber(), e.getMessage());
            markErrorRect(rect, "Error subsanación: " + e.getMessage());
        }
    }

    // ================================================================
    //  Actualización de estado
    // ================================================================


    private void markError(Invoice invoice, String msg) {
        invoice.setAeatStatus(AeatStatus.REJECTED);
        invoice.setAeatLastError(msg);
        invoice.setAeatRetryCount(invoice.getAeatRetryCount() + 1);
        invoice.setAeatRejectionReason(AeatRejectionReason.NETWORK_ERROR);
        invoiceRepository.save(invoice);
    }

    private void markErrorTicket(Ticket ticket, String msg) {
        ticket.setAeatStatus(AeatStatus.REJECTED);
        ticket.setAeatLastError(msg);
        ticket.setAeatRetryCount(ticket.getAeatRetryCount() + 1);
        ticket.setAeatRejectionReason(AeatRejectionReason.NETWORK_ERROR);
        ticketRepository.save(ticket);
    }

    private void markErrorRect(RectificativeInvoice rect, String msg) {
        rect.setAeatStatus(AeatStatus.REJECTED);
        rect.setAeatLastError(msg);
        rect.setAeatRetryCount(rect.getAeatRetryCount() + 1);
        rect.setAeatRejectionReason(AeatRejectionReason.NETWORK_ERROR);
        rectRepository.save(rect);
    }

    private void markValidationError(Invoice inv, String msg) {
        inv.setAeatStatus(AeatStatus.REJECTED);
        inv.setAeatLastError("Error validación: " + msg);
        inv.setAeatRejectionReason(AeatRejectionReason.VALIDATION_ERROR);
        invoiceRepository.save(inv);
    }

    private void markValidationErrorTicket(Ticket t, String msg) {
        t.setAeatStatus(AeatStatus.REJECTED);
        t.setAeatLastError("Error validación: " + msg);
        t.setAeatRejectionReason(AeatRejectionReason.VALIDATION_ERROR);
        ticketRepository.save(t);
    }

    private void markValidationErrorRect(RectificativeInvoice r, String msg) {
        r.setAeatStatus(AeatStatus.REJECTED);
        r.setAeatLastError("Error validación: " + msg);
        r.setAeatRejectionReason(AeatRejectionReason.VALIDATION_ERROR);
        rectRepository.save(r);
    }

    // ================================================================
    //  Helpers
    // ================================================================

    private CompanySettings getCompany() {
        return companySettingsRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("CompanySettings no configurado"));
    }

    private String buildSoftwareNombre(CompanySettings company) {
        String nombre = (company.getAppName() != null ? company.getAppName() : company.getName()) + " TPV";
        return nombre.length() > 30 ? nombre.substring(0, 30) : nombre;
    }

    private boolean isReadyForRetry(LocalDateTime submissionDate, int retryCount, AeatRejectionReason reason) {
        if (submissionDate == null) return true;
        if (reason == AeatRejectionReason.VALIDATION_ERROR) return false;

        long waitSeconds;
        if (retryCount <= 1) waitSeconds = 60;
        else if (retryCount == 2) waitSeconds = 120;
        else if (retryCount == 3) waitSeconds = 300;
        else if (retryCount == 4) waitSeconds = 900;
        else if (retryCount == 5) waitSeconds = 1800;
        else waitSeconds = 3600;

        return LocalDateTime.now().isAfter(submissionDate.plusSeconds(waitSeconds));
    }

    private AeatRejectionReason classifyRejection(String estado, String descripcion) {
        if (estado != null) {
            // Códigos 1xx (faltan parámetros), 2xx (formato), 4xx (negocio) son errores de validación
            if (estado.startsWith("1") || estado.startsWith("2") || estado.startsWith("4")) {
                return AeatRejectionReason.VALIDATION_ERROR;
            }
            if (estado.contains("Conexion") || estado.contains("Timeout") || estado.contains("HTTP_")) {
                return AeatRejectionReason.NETWORK_ERROR;
            }
        }
        if (descripcion != null) {
            String d = descripcion.toLowerCase();
            if (descripcion.startsWith("Incorrecto:") ||
                d.contains("duplicado") ||
                d.contains("nif no está identificado") ||
                d.contains("bloque") ||
                d.contains("censo de la aeat")) {
                return AeatRejectionReason.VALIDATION_ERROR;
            }
        }
        return AeatRejectionReason.NETWORK_ERROR;
    }
}
