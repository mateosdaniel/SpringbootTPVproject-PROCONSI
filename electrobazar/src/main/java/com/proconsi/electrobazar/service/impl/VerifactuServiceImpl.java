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
    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;
    private final RectificativeInvoiceRepository rectRepository;
    private final CompanySettingsRepository companySettingsRepository;

    // ================================================================
    //  Submit async methods
    // ================================================================

    @Override
    @Async
    @Transactional
    public void submitInvoiceAsync(Long invoiceId) {
        if (!props.isEnabled()) return;
        invoiceRepository.findById(invoiceId).ifPresent(this::submitInvoice);
    }

    @Override
    @Async
    @Transactional
    public void submitTicketAsync(Long ticketId) {
        if (!props.isEnabled()) return;
        ticketRepository.findById(ticketId).ifPresent(this::submitTicket);
    }

    @Override
    @Async
    @Transactional
    public void submitRectificativeAsync(Long rectId) {
        if (!props.isEnabled()) return;
        rectRepository.findById(rectId).ifPresent(this::submitRectificative);
    }

    // ================================================================
    //  Retry (llamado desde el scheduler)
    // ================================================================

    @Override
    @Transactional
    public void retryPendingSend() {
        if (!props.isEnabled()) return;
        int max = props.getRetry().getMaxAttempts();

        invoiceRepository.findPendingSend(max)
                .forEach(this::submitInvoice);

        ticketRepository.findPendingSend(max)
                .forEach(this::submitTicket);

        rectRepository.findPendingSend(max)
                .forEach(this::submitRectificative);
    }

    // ================================================================
    //  Lógica de envío
    // ================================================================

    private void submitInvoice(Invoice invoice) {
        CompanySettings company = getCompany();
        try {
            String xml = xmlBuilder.buildAltaInvoice(invoice, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando factura {} a AEAT [entorno: {}]",
                    invoice.getInvoiceNumber(), props.getEnvironment());

            VerifactuSoapClient.AeatResponse resp = soapClient.send(xml);
            updateInvoiceStatus(invoice, resp);
        } catch (Exception e) {
            log.error("Verifactu: error procesando factura {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            markError(invoice, e.getMessage());
        }
    }

    private void submitTicket(Ticket ticket) {
        CompanySettings company = getCompany();
        try {
            String xml = xmlBuilder.buildAltaTicket(ticket, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando ticket {} a AEAT [entorno: {}]",
                    ticket.getTicketNumber(), props.getEnvironment());

            VerifactuSoapClient.AeatResponse resp = soapClient.send(xml);
            updateTicketStatus(ticket, resp);
        } catch (Exception e) {
            log.error("Verifactu: error procesando ticket {}: {}", ticket.getTicketNumber(), e.getMessage(), e);
            markErrorTicket(ticket, e.getMessage());
        }
    }

    private void submitRectificative(RectificativeInvoice rect) {
        CompanySettings company = getCompany();
        try {
            String xml = xmlBuilder.buildAltaRectificative(rect, company,
                    buildSoftwareNombre(company), props.getSoftware().getIdSistema(),
                    props.getSoftware().getVersion(), props.getSoftware().getNumeroInstalacion());

            log.info("Verifactu: enviando rectificativa {} a AEAT [entorno: {}]",
                    rect.getRectificativeNumber(), props.getEnvironment());

            VerifactuSoapClient.AeatResponse resp = soapClient.send(xml);
            updateRectStatus(rect, resp);
        } catch (Exception e) {
            log.error("Verifactu: error procesando rectificativa {}: {}", rect.getRectificativeNumber(), e.getMessage(), e);
            markErrorRect(rect, e.getMessage());
        }
    }

    // ================================================================
    //  Actualización de estado
    // ================================================================

    private void updateInvoiceStatus(Invoice invoice, VerifactuSoapClient.AeatResponse resp) {
        invoice.setAeatSubmissionDate(LocalDateTime.now());
        if (resp.success()) {
            invoice.setAeatStatus("AceptadaConErrores".equalsIgnoreCase(resp.estado())
                    ? AeatStatus.ACCEPTED_WITH_ERRORS : AeatStatus.ACCEPTED);
            invoice.setAeatLastError(resp.descripcion());
            log.info("Verifactu: factura {} aceptada [{}]", invoice.getInvoiceNumber(), resp.estado());
        } else {
            invoice.setAeatStatus(AeatStatus.REJECTED);
            invoice.setAeatLastError(resp.estado() + ": " + resp.descripcion());
            invoice.setAeatRetryCount(invoice.getAeatRetryCount() + 1);
            log.warn("Verifactu: factura {} rechazada: {}", invoice.getInvoiceNumber(), resp.descripcion());
        }
        invoiceRepository.save(invoice);
    }

    private void updateTicketStatus(Ticket ticket, VerifactuSoapClient.AeatResponse resp) {
        ticket.setAeatSubmissionDate(LocalDateTime.now());
        if (resp.success()) {
            ticket.setAeatStatus("AceptadaConErrores".equalsIgnoreCase(resp.estado())
                    ? AeatStatus.ACCEPTED_WITH_ERRORS : AeatStatus.ACCEPTED);
            ticket.setAeatLastError(resp.descripcion());
            log.info("Verifactu: ticket {} aceptado [{}]", ticket.getTicketNumber(), resp.estado());
        } else {
            ticket.setAeatStatus(AeatStatus.REJECTED);
            ticket.setAeatLastError(resp.estado() + ": " + resp.descripcion());
            ticket.setAeatRetryCount(ticket.getAeatRetryCount() + 1);
            log.warn("Verifactu: ticket {} rechazado: {}", ticket.getTicketNumber(), resp.descripcion());
        }
        ticketRepository.save(ticket);
    }

    private void updateRectStatus(RectificativeInvoice rect, VerifactuSoapClient.AeatResponse resp) {
        rect.setAeatSubmissionDate(LocalDateTime.now());
        if (resp.success()) {
            rect.setAeatStatus("AceptadaConErrores".equalsIgnoreCase(resp.estado())
                    ? AeatStatus.ACCEPTED_WITH_ERRORS : AeatStatus.ACCEPTED);
            rect.setAeatLastError(resp.descripcion());
            log.info("Verifactu: rectificativa {} aceptada [{}]", rect.getRectificativeNumber(), resp.estado());
        } else {
            rect.setAeatStatus(AeatStatus.REJECTED);
            rect.setAeatLastError(resp.estado() + ": " + resp.descripcion());
            rect.setAeatRetryCount(rect.getAeatRetryCount() + 1);
            log.warn("Verifactu: rectificativa {} rechazada: {}", rect.getRectificativeNumber(), resp.descripcion());
        }
        rectRepository.save(rect);
    }

    private void markError(Invoice invoice, String msg) {
        invoice.setAeatStatus(AeatStatus.REJECTED);
        invoice.setAeatLastError(msg);
        invoice.setAeatRetryCount(invoice.getAeatRetryCount() + 1);
        invoiceRepository.save(invoice);
    }

    private void markErrorTicket(Ticket ticket, String msg) {
        ticket.setAeatStatus(AeatStatus.REJECTED);
        ticket.setAeatLastError(msg);
        ticket.setAeatRetryCount(ticket.getAeatRetryCount() + 1);
        ticketRepository.save(ticket);
    }

    private void markErrorRect(RectificativeInvoice rect, String msg) {
        rect.setAeatStatus(AeatStatus.REJECTED);
        rect.setAeatLastError(msg);
        rect.setAeatRetryCount(rect.getAeatRetryCount() + 1);
        rectRepository.save(rect);
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
}
