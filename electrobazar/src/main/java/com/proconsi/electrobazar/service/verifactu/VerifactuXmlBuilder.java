package com.proconsi.electrobazar.service.verifactu;

import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.RectificativeInvoiceRepository;
import com.proconsi.electrobazar.repository.TicketRepository;
import com.proconsi.electrobazar.util.VerifactuHashCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proconsi.electrobazar.dto.SubsanarRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye el XML SOAP conforme al esquema AEAT VeriFactu
 * (SuministroLR.xsd + SuministroInformacion.xsd).
 */
@Component
@RequiredArgsConstructor
public class VerifactuXmlBuilder {

    private static final String NS_LR = "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroLR.xsd";
    private static final String NS_SF = "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd";

    private final VerifactuHashCalculator hashCalculator;
    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;
    private final RectificativeInvoiceRepository rectRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    // ================================================================
    // Factura completa (F1)
    // ================================================================

    public String buildAltaInvoice(Invoice invoice, CompanySettings company,
             String softwareNombre, String softwareId,
             String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        return soapEnvelopeOpen() + regFactuOpen(nif, company.getName()) + registroFacturaOpen() +
               buildAltaInvoiceBody(invoice, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, false) +
               registroFacturaClose() + regFactuClose() + soapEnvelopeClose();
    }

    public String buildSubsanacionInvoice(Invoice invoice, CompanySettings company,
                                          String softwareNombre, String softwareId,
                                          String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        return soapEnvelopeOpen() + regFactuOpen(nif, company.getName()) + registroFacturaOpen() +
               buildAltaInvoiceBody(invoice, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, true) +
               registroFacturaClose() + regFactuClose() + soapEnvelopeClose();
    }

    public String buildAltaTicket(Ticket ticket, CompanySettings company,
            String softwareNombre, String softwareId,
            String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        return soapEnvelopeOpen() + regFactuOpen(nif, company.getName()) + registroFacturaOpen() +
               buildAltaTicketBody(ticket, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, false) +
               registroFacturaClose() + regFactuClose() + soapEnvelopeClose();
    }

    public String buildSubsanacionTicket(Ticket ticket, CompanySettings company,
                                         String softwareNombre, String softwareId,
                                         String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        return soapEnvelopeOpen() + regFactuOpen(nif, company.getName()) + registroFacturaOpen() +
               buildAltaTicketBody(ticket, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, true) +
               registroFacturaClose() + regFactuClose() + soapEnvelopeClose();
    }

    // ================================================================
    //  BATCHING (FEATURE 1)
    // ================================================================

    public String buildBatch(List<Object> records, CompanySettings company,
                             String softwareNombre, String softwareId,
                             String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        StringBuilder sb = new StringBuilder();
        sb.append(soapEnvelopeOpen());
        sb.append(regFactuOpen(nif, company.getName()));

        for (Object record : records) {
            sb.append(registroFacturaOpen());
            if (record instanceof Invoice i) {
                // Determine if it's an annulment based on status or some other flag?
                // For now, assume it's Alta if we are in this flow, or check if we need to support annulment batching.
                // The request says: "Each can contain either RegistroAlta or RegistroAnulacion."
                // I'll check a custom property or status.
                if (i.getAeatStatus() == AeatStatus.REJECTED && i.getAeatLastError() != null && i.getAeatLastError().contains("anulación")) {
                     sb.append(buildAnulacionInvoiceBody(i, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion));
                } else {
                     sb.append(buildAltaInvoiceBody(i, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, false));
                }
            } else if (record instanceof Ticket t) {
                if (t.getAeatStatus() == AeatStatus.REJECTED && t.getAeatLastError() != null && t.getAeatLastError().contains("anulación")) {
                    sb.append(buildAnulacionTicketBody(t, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion));
                } else {
                    sb.append(buildAltaTicketBody(t, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, false));
                }
            } else if (record instanceof RectificativeInvoice r) {
                sb.append(buildAltaRectificativeBody(r, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, false));
            }
            sb.append(registroFacturaClose());
        }

        sb.append(regFactuClose());
        sb.append(soapEnvelopeClose());
        return sb.toString();
    }

    private String buildAltaInvoiceBody(Invoice invoice, CompanySettings company,
                                        String softwareNombre, String softwareId,
                                        String softwareVersion, String softwareInstalacion,
                                        boolean isSubsanacion) {
        Sale sale = invoice.getSale();
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(invoice.getCreatedAt());
        StringBuilder sb = new StringBuilder();
        sb.append("        <sf:RegistroAlta>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append(idFactura(nif, invoice.getInvoiceNumber(), fechaExp));
        sb.append(tag("sf:NombreRazonEmisor", esc(company.getName())));
        if (isSubsanacion) {
            sb.append("          <sf:Subsanacion>S</sf:Subsanacion>\n");
        }
        sb.append(tag("sf:TipoFactura", "F1"));
        sb.append(tag("sf:DescripcionOperacion", "Venta TPV " + invoice.getInvoiceNumber()));

        SubsanarRequest puntual = getPuntualData(sale);
        if (sale.getCustomer() == null && puntual == null) {
            sb.append(tag("sf:FacturaSinIdentifDestinatarioArt61d", "S"));
            sb.append("          <sf:Destinatarios>\n");
            sb.append("            <sf:IDDestinatario>\n");
            sb.append(tag("sf:NombreRazon", "CLIENTE FINAL"));
            sb.append(tag("sf:NIF", "000000000"));
            sb.append("            </sf:IDDestinatario>\n");
            sb.append("          </sf:Destinatarios>\n");
        } else {
            sb.append("          <sf:Destinatarios>\n");
            sb.append("            <sf:IDDestinatario>\n");
            if (sale.getCustomer() != null) {
                sb.append(tag("sf:NombreRazon", esc(sale.getCustomer().getName())));
                String taxId = sale.getCustomer().getTaxId();
                sb.append(tag("sf:NIF", taxId != null ? taxId.trim() : "000000000"));
            } else {
                sb.append(tag("sf:NombreRazon", esc(puntual.getNombreRazon())));
                sb.append(tag("sf:NIF", puntual.getNif() != null ? puntual.getNif().trim() : "000000000"));
            }
            sb.append("            </sf:IDDestinatario>\n");
            sb.append("          </sf:Destinatarios>\n");
        }

        sb.append(desglose(sale));
        BigDecimal cuotaTotal = sale.getTotalVat().add(sale.getTotalRecargo()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal importeTotal = sale.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        sb.append(tag("sf:CuotaTotal", fmt(cuotaTotal)));
        sb.append(tag("sf:ImporteTotal", fmt(importeTotal)));
        sb.append(encadenamiento(invoice.getHashPreviousInvoice(), nif, getPreviousNumSerie(invoice), getPreviousFecha(invoice)));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre, softwareId, softwareVersion, softwareInstalacion));
        
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(LocalDateTime.now());
        String huellaEnvio = hashCalculator.calculate(nif, invoice.getInvoiceNumber(), invoice.getCreatedAt(), "F1", cuotaTotal, importeTotal, invoice.getHashPreviousInvoice(), fechaHoraHuso);
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", huellaEnvio));
        sb.append("        </sf:RegistroAlta>\n");
        return sb.toString();
    }

    private String buildAltaTicketBody(Ticket ticket, CompanySettings company,
                                       String softwareNombre, String softwareId,
                                       String softwareVersion, String softwareInstalacion,
                                       boolean isSubsanacion) {
        Sale sale = ticket.getSale();
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(ticket.getCreatedAt());
        StringBuilder sb = new StringBuilder();
        sb.append("        <sf:RegistroAlta>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append(idFactura(nif, ticket.getTicketNumber(), fechaExp));
        sb.append(tag("sf:NombreRazonEmisor", esc(company.getName())));
        if (isSubsanacion) {
            sb.append("          <sf:Subsanacion>S</sf:Subsanacion>\n");
        }
        sb.append(tag("sf:TipoFactura", "F2"));
        sb.append(tag("sf:DescripcionOperacion", "Venta TPV " + ticket.getTicketNumber()));
        sb.append(desglose(sale));
        BigDecimal cuotaTotal = sale.getTotalVat().add(sale.getTotalRecargo()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal importeTotal = sale.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        sb.append(tag("sf:CuotaTotal", fmt(cuotaTotal)));
        sb.append(tag("sf:ImporteTotal", fmt(importeTotal)));
        sb.append(encadenamientoTicket(ticket.getHashPreviousInvoice(), nif, getPreviousTicketNumSerie(ticket), getPreviousTicketFecha(ticket)));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre, softwareId, softwareVersion, softwareInstalacion));
        
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(LocalDateTime.now());
        String huellaEnvio = hashCalculator.calculate(nif, ticket.getTicketNumber(), ticket.getCreatedAt(), "F2", cuotaTotal, importeTotal, ticket.getHashPreviousInvoice(), fechaHoraHuso);
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", huellaEnvio));
        sb.append("        </sf:RegistroAlta>\n");
        return sb.toString();
    }

    private String buildAltaRectificativeBody(RectificativeInvoice rect, CompanySettings company,
                                              String softwareNombre, String softwareId,
                                              String softwareVersion, String softwareInstalacion,
                                              boolean isSubsanacion) {
        SaleReturn saleReturn = rect.getSaleReturn();
        Sale originalSale = saleReturn.getOriginalSale();
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(rect.getCreatedAt());
        BigDecimal totalRefundedNeg = saleReturn.getTotalRefunded().negate();
        BigDecimal cuotaTotal = estimateCuota(saleReturn, originalSale).negate().setScale(2, RoundingMode.HALF_UP);

        StringBuilder sb = new StringBuilder();
        sb.append("        <sf:RegistroAlta>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append(idFactura(nif, rect.getRectificativeNumber(), fechaExp));
        sb.append(tag("sf:NombreRazonEmisor", esc(company.getName())));
        if (isSubsanacion) {
            sb.append("          <sf:Subsanacion>S</sf:Subsanacion>\n");
        }
        boolean isOriginalTicket = rect.getOriginalTicket() != null;
        String tipoFactura = isOriginalTicket ? "R5" : "R4";
        String originalNum = isOriginalTicket ? rect.getOriginalTicket().getTicketNumber() : rect.getOriginalInvoice().getInvoiceNumber();
        LocalDateTime originalFecha = isOriginalTicket ? rect.getOriginalTicket().getCreatedAt() : rect.getOriginalInvoice().getCreatedAt();
        sb.append(tag("sf:TipoFactura", tipoFactura));
        sb.append(tag("sf:TipoRectificativa", "I"));
        sb.append("          <sf:FacturasRectificadas>\n");
        sb.append("            <sf:IDFacturaRectificada>\n");
        sb.append(tag("sf:IDEmisorFactura", nif));
        sb.append(tag("sf:NumSerieFactura", originalNum));
        sb.append(tag("sf:FechaExpedicionFactura", hashCalculator.getFechaExpedicion(originalFecha)));
        sb.append("            </sf:IDFacturaRectificada>\n");
        sb.append("          </sf:FacturasRectificadas>\n");
        sb.append(tag("sf:DescripcionOperacion", "Rectificación " + originalNum + ". " + esc(rect.getReason())));
        if (!isOriginalTicket) {
            SubsanarRequest puntual = getPuntualData(originalSale);
            if (originalSale.getCustomer() == null && puntual == null) {
                sb.append(tag("sf:FacturaSinIdentifDestinatarioArt61d", "S"));
                sb.append("          <sf:Destinatarios>\n");
                sb.append("            <sf:IDDestinatario>\n");
                sb.append(tag("sf:NombreRazon", "CLIENTE FINAL"));
                sb.append(tag("sf:NIF", "000000000"));
                sb.append("            </sf:IDDestinatario>\n");
                sb.append("          </sf:Destinatarios>\n");
            } else {
                sb.append("          <sf:Destinatarios>\n");
                sb.append("            <sf:IDDestinatario>\n");
                if (originalSale.getCustomer() != null) {
                    sb.append(tag("sf:NombreRazon", esc(originalSale.getCustomer().getName())));
                    String taxId = originalSale.getCustomer().getTaxId();
                    sb.append(tag("sf:NIF", taxId != null ? taxId.trim() : "000000000"));
                } else {
                    sb.append(tag("sf:NombreRazon", esc(puntual.getNombreRazon())));
                    sb.append(tag("sf:NIF", puntual.getNif() != null ? puntual.getNif().trim() : "000000000"));
                }
                sb.append("            </sf:IDDestinatario>\n");
                sb.append("          </sf:Destinatarios>\n");
            }
        }
        sb.append(desgloseRectificativo(saleReturn, originalSale));
        sb.append(tag("sf:CuotaTotal", fmt(cuotaTotal)));
        sb.append(tag("sf:ImporteTotal", fmt(totalRefundedNeg.setScale(2, RoundingMode.HALF_UP))));
        sb.append(encadenamiento(rect.getHashPreviousInvoice(), nif, getPreviousRectNumSerie(rect), getPreviousRectFecha(rect)));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre, softwareId, softwareVersion, softwareInstalacion));
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(LocalDateTime.now());
        String huellaEnvio = hashCalculator.calculate(nif, rect.getRectificativeNumber(), rect.getCreatedAt(), tipoFactura, cuotaTotal, totalRefundedNeg.setScale(2, RoundingMode.HALF_UP), rect.getHashPreviousInvoice(), fechaHoraHuso);
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", huellaEnvio));
        sb.append("        </sf:RegistroAlta>\n");
        return sb.toString();
    }

    private String buildAnulacionInvoiceBody(Invoice invoice, CompanySettings company,
                                             String softwareNombre, String softwareId,
                                             String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(invoice.getCreatedAt());
        StringBuilder sb = new StringBuilder();
        sb.append("        <sf:RegistroAnulacion>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append("          <sf:IDFactura>\n");
        sb.append(tag("sf:IDEmisorFacturaAnulada", nif));
        sb.append(tag("sf:NumSerieFacturaAnulada", invoice.getInvoiceNumber()));
        sb.append(tag("sf:FechaExpedicionFacturaAnulada", fechaExp));
        sb.append("          </sf:IDFactura>\n");
        LastRecordInfo last = getLatestRecordInfo();
        sb.append(encadenamientoAnulacion(last, nif));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre, softwareId, softwareVersion, softwareInstalacion));
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(LocalDateTime.now());
        String huellaEnvio = hashCalculator.calculateAnulacionHash(nif, invoice.getInvoiceNumber(), invoice.getCreatedAt(), last.hash, fechaHoraHuso);
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", huellaEnvio));
        sb.append("        </sf:RegistroAnulacion>\n");
        return sb.toString();
    }

    private String buildAnulacionTicketBody(Ticket ticket, CompanySettings company,
                                            String softwareNombre, String softwareId,
                                            String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(ticket.getCreatedAt());
        StringBuilder sb = new StringBuilder();
        sb.append("        <sf:RegistroAnulacion>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append("          <sf:IDFactura>\n");
        sb.append(tag("sf:IDEmisorFacturaAnulada", nif));
        sb.append(tag("sf:NumSerieFacturaAnulada", ticket.getTicketNumber()));
        sb.append(tag("sf:FechaExpedicionFacturaAnulada", fechaExp));
        sb.append("          </sf:IDFactura>\n");
        LastRecordInfo last = getLatestRecordInfo();
        sb.append(encadenamientoAnulacion(last, nif));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre, softwareId, softwareVersion, softwareInstalacion));
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(LocalDateTime.now());
        String huellaEnvio = hashCalculator.calculateAnulacionHash(nif, ticket.getTicketNumber(), ticket.getCreatedAt(), last.hash, fechaHoraHuso);
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", huellaEnvio));
        sb.append("        </sf:RegistroAnulacion>\n");
        return sb.toString();
    }

    // ================================================================
    // Factura rectificativa (R1)
    // ================================================================

    public String buildAltaRectificative(RectificativeInvoice rect, CompanySettings company,
            String softwareNombre, String softwareId,
            String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        return soapEnvelopeOpen() + regFactuOpen(nif, company.getName()) + registroFacturaOpen() +
               buildAltaRectificativeBody(rect, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, false) +
               registroFacturaClose() + regFactuClose() + soapEnvelopeClose();
    }

    public String buildSubsanacionRectificative(RectificativeInvoice rect, CompanySettings company,
                                                String softwareNombre, String softwareId,
                                                String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        return soapEnvelopeOpen() + regFactuOpen(nif, company.getName()) + registroFacturaOpen() +
               buildAltaRectificativeBody(rect, company, softwareNombre, softwareId, softwareVersion, softwareInstalacion, true) +
               registroFacturaClose() + regFactuClose() + soapEnvelopeClose();
    }


    // ================================================================
    //  Subsanación (ACCEPTED_WITH_ERRORS flow)
    // ================================================================

    // buildSubsanacion methods removed as they are now integrated into Internal methods or called buildXxxInternal(..., true)

    // ================================================================
    // Anulación de Factura
    // ================================================================

    public String buildAnulacionInvoice(Invoice invoice, CompanySettings company,
            String softwareNombre, String softwareId,
            String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(invoice.getCreatedAt());

        StringBuilder sb = new StringBuilder();
        sb.append(soapEnvelopeOpen());
        sb.append(regFactuOpen(nif, company.getName()));
        sb.append(registroFacturaOpen());

        sb.append("        <sf:RegistroAnulacion>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append("          <sf:IDFactura>\n");
        sb.append(tag("sf:IDEmisorFacturaAnulada", nif));
        sb.append(tag("sf:NumSerieFacturaAnulada", invoice.getInvoiceNumber()));
        sb.append(tag("sf:FechaExpedicionFacturaAnulada", fechaExp));
        sb.append("          </sf:IDFactura>\n");

        // Encadenamiento global VeriFactu
        LastRecordInfo last = getLatestRecordInfo();
        sb.append(encadenamientoAnulacion(last, nif));

        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre,
                softwareId, softwareVersion, softwareInstalacion));

        LocalDateTime ahora = LocalDateTime.now();
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(ahora);
        
        // El hash de anulación usa la huella del ÚLTIMO registro enviado al sistema (no de la propia factura)
        String huellaEnvio = hashCalculator.calculateAnulacionHash(nif, invoice.getInvoiceNumber(),
                invoice.getCreatedAt(), last.hash, fechaHoraHuso);

        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", huellaEnvio));
        sb.append("        </sf:RegistroAnulacion>\n");

        sb.append(registroFacturaClose());
        sb.append(regFactuClose());
        sb.append(soapEnvelopeClose());
        return sb.toString();
    }

    public String buildAnulacionTicket(Ticket ticket, CompanySettings company,
            String softwareNombre, String softwareId,
            String softwareVersion, String softwareInstalacion) {
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(ticket.getCreatedAt());

        StringBuilder sb = new StringBuilder();
        sb.append(soapEnvelopeOpen());
        sb.append(regFactuOpen(nif, company.getName()));
        sb.append(registroFacturaOpen());

        sb.append("        <sf:RegistroAnulacion>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append("          <sf:IDFactura>\n");
        sb.append(tag("sf:IDEmisorFacturaAnulada", nif));
        sb.append(tag("sf:NumSerieFacturaAnulada", ticket.getTicketNumber()));
        sb.append(tag("sf:FechaExpedicionFacturaAnulada", fechaExp));
        sb.append("          </sf:IDFactura>\n");

        // Encadenamiento global VeriFactu
        LastRecordInfo last = getLatestRecordInfo();
        sb.append(encadenamientoAnulacion(last, nif));

        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre,
                softwareId, softwareVersion, softwareInstalacion));

        LocalDateTime ahora = LocalDateTime.now();
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(ahora);
        
        String huellaEnvio = hashCalculator.calculateAnulacionHash(nif, ticket.getTicketNumber(),
                ticket.getCreatedAt(), last.hash, fechaHoraHuso);

        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", huellaEnvio));
        sb.append("        </sf:RegistroAnulacion>\n");

        sb.append(registroFacturaClose());
        sb.append(regFactuClose());
        sb.append(soapEnvelopeClose());
        return sb.toString();
    }

    private static class LastRecordInfo {
        String hash = VerifactuHashCalculator.INITIAL_HASH;
        String numSerie = null;
        String fecha = null;
    }

    private LastRecordInfo getLatestRecordInfo() {
        // Buscar el último registro procesado por la AEAT (Invoice, Ticket o Rectificative)
        Invoice lastInv = invoiceRepository.findAll().stream()
                .filter(i -> i.getAeatSubmissionDate() != null)
                .max((a, b) -> a.getAeatSubmissionDate().compareTo(b.getAeatSubmissionDate()))
                .orElse(null);
        
        Ticket lastTick = ticketRepository.findAll().stream()
                .filter(t -> t.getAeatSubmissionDate() != null)
                .max((a, b) -> a.getAeatSubmissionDate().compareTo(b.getAeatSubmissionDate()))
                .orElse(null);
                
        RectificativeInvoice lastRect = rectRepository.findAll().stream()
                .filter(r -> r.getAeatSubmissionDate() != null)
                .max((a, b) -> a.getAeatSubmissionDate().compareTo(b.getAeatSubmissionDate()))
                .orElse(null);

        Object winner = null;
        LocalDateTime maxDate = LocalDateTime.MIN;

        if (lastInv != null && lastInv.getAeatSubmissionDate().isAfter(maxDate)) {
            maxDate = lastInv.getAeatSubmissionDate();
            winner = lastInv;
        }
        if (lastTick != null && lastTick.getAeatSubmissionDate().isAfter(maxDate)) {
            maxDate = lastTick.getAeatSubmissionDate();
            winner = lastTick;
        }
        if (lastRect != null && lastRect.getAeatSubmissionDate().isAfter(maxDate)) {
            maxDate = lastRect.getAeatSubmissionDate();
            winner = lastRect;
        }

        LastRecordInfo info = new LastRecordInfo();
        if (winner instanceof Invoice i) {
            info.hash = i.getHashCurrentInvoice();
            info.numSerie = i.getInvoiceNumber();
            info.fecha = hashCalculator.getFechaExpedicion(i.getCreatedAt());
        } else if (winner instanceof Ticket t) {
            info.hash = t.getHashCurrentInvoice();
            info.numSerie = t.getTicketNumber();
            info.fecha = hashCalculator.getFechaExpedicion(t.getCreatedAt());
        } else if (winner instanceof RectificativeInvoice r) {
            info.hash = r.getHashCurrentInvoice();
            info.numSerie = r.getRectificativeNumber();
            info.fecha = hashCalculator.getFechaExpedicion(r.getCreatedAt());
        }
        return info;
    }

    private String encadenamientoAnulacion(LastRecordInfo last, String nif) {
        return encadenamiento(last.hash, nif, last.numSerie, last.fecha);
    }

    // ================================================================
    // Helpers - estructura XML
    // ================================================================

    private String soapEnvelopeOpen() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">\n"
                + "  <soapenv:Header/>\n"
                + "  <soapenv:Body>\n";
    }

    private String soapEnvelopeClose() {
        return "  </soapenv:Body>\n</soapenv:Envelope>";
    }

    private String regFactuOpen(String nif, String nombre) {
        return "    <sfLR:RegFactuSistemaFacturacion\n"
                + "        xmlns:sfLR=\"" + NS_LR + "\"\n"
                + "        xmlns:sf=\"" + NS_SF + "\">\n"
                + "      <sfLR:Cabecera>\n"
                + "        <sf:ObligadoEmision>\n"
                + tag("sf:NombreRazon", esc(nombre))
                + tag("sf:NIF", nif)
                + "        </sf:ObligadoEmision>\n"
                + "      </sfLR:Cabecera>\n";
    }

    private String regFactuClose() {
        return "    </sfLR:RegFactuSistemaFacturacion>\n";
    }

    private String registroFacturaOpen() {
        return "      <sfLR:RegistroFactura>\n";
    }

    private String registroFacturaClose() {
        return "      </sfLR:RegistroFactura>\n";
    }

    private String idFactura(String nif, String numSerie, String fecha) {
        return "          <sf:IDFactura>\n"
                + tag("sf:IDEmisorFactura", nif)
                + tag("sf:NumSerieFactura", numSerie)
                + tag("sf:FechaExpedicionFactura", fecha)
                + "          </sf:IDFactura>\n";
    }

    private String desglose(Sale sale) {
        // Agrupar líneas por tipo IVA
        Map<BigDecimal, BigDecimal[]> groups = new LinkedHashMap<>();
        for (SaleLine line : sale.getLines()) {
            BigDecimal rate = line.getVatRate();
            groups.merge(rate, new BigDecimal[] {
                    line.getBaseAmount(), line.getVatAmount(), line.getRecargoRate(), line.getRecargoAmount()
            }, (a, b) -> new BigDecimal[] {
                    a[0].add(b[0]), a[1].add(b[1]), b[2], a[3].add(b[3])
            });
        }
        // Si no hay líneas (caso raro), fallback al total de la venta
        if (groups.isEmpty()) {
            groups.put(BigDecimal.ZERO, new BigDecimal[] {
                    sale.getTotalBase(), sale.getTotalVat(), BigDecimal.ZERO, sale.getTotalRecargo()
            });
        }
        StringBuilder sb = new StringBuilder("          <sf:Desglose>\n");
        for (Map.Entry<BigDecimal, BigDecimal[]> e : groups.entrySet()) {
            BigDecimal vatRate = e.getKey();
            BigDecimal[] v = e.getValue();
            sb.append("            <sf:DetalleDesglose>\n");
            sb.append(tag("sf:Impuesto", "01"));
            sb.append(tag("sf:ClaveRegimen", "01"));
            sb.append(tag("sf:CalificacionOperacion", "S1"));
            sb.append(tag("sf:TipoImpositivo",
                    fmt(vatRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:BaseImponibleOimporteNoSujeto", fmt(v[0].setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:CuotaRepercutida", fmt(v[1].setScale(2, RoundingMode.HALF_UP))));
            if (v[2].compareTo(BigDecimal.ZERO) > 0) {
                sb.append(tag("sf:TipoRecargoEquivalencia",
                        fmt(v[2].multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
                sb.append(tag("sf:CuotaRecargoEquivalencia", fmt(v[3].setScale(2, RoundingMode.HALF_UP))));
            }
            sb.append("            </sf:DetalleDesglose>\n");
        }
        sb.append("          </sf:Desglose>\n");
        return sb.toString();
    }

    private String desgloseRectificativo(SaleReturn saleReturn, Sale originalSale) {
        // Construir el desglose negativo a partir de las líneas devueltas y sus
        // SaleLines originales
        Map<BigDecimal, BigDecimal[]> groups = new LinkedHashMap<>();
        for (ReturnLine rl : saleReturn.getLines()) {
            SaleLine sl = rl.getSaleLine();
            if (sl == null)
                continue;
            BigDecimal ratio = rl.getQuantity().abs()
                    .divide(sl.getQuantity().abs(), 10, RoundingMode.HALF_UP);
            BigDecimal base = sl.getBaseAmount().multiply(ratio).negate();
            BigDecimal vat = sl.getVatAmount().multiply(ratio).negate();
            BigDecimal reRate = sl.getRecargoRate();
            BigDecimal re = sl.getRecargoAmount().multiply(ratio).negate();
            groups.merge(sl.getVatRate(), new BigDecimal[] { base, vat, reRate, re },
                    (a, b) -> new BigDecimal[] { a[0].add(b[0]), a[1].add(b[1]), b[2], a[3].add(b[3]) });
        }
        if (groups.isEmpty()) {
            BigDecimal ratio = originalSale.getTotalAmount().compareTo(BigDecimal.ZERO) != 0
                    ? saleReturn.getTotalRefunded().divide(originalSale.getTotalAmount().abs(), 10,
                            RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            groups.put(BigDecimal.ZERO, new BigDecimal[] {
                    originalSale.getTotalBase().multiply(ratio).negate(),
                    originalSale.getTotalVat().multiply(ratio).negate(),
                    BigDecimal.ZERO,
                    originalSale.getTotalRecargo().multiply(ratio).negate()
            });
        }
        StringBuilder sb = new StringBuilder("          <sf:Desglose>\n");
        for (Map.Entry<BigDecimal, BigDecimal[]> e : groups.entrySet()) {
            BigDecimal vatRate = e.getKey();
            BigDecimal[] v = e.getValue();
            sb.append("            <sf:DetalleDesglose>\n");
            sb.append(tag("sf:Impuesto", "01"));
            sb.append(tag("sf:ClaveRegimen", "01"));
            sb.append(tag("sf:CalificacionOperacion", "S1"));
            sb.append(tag("sf:TipoImpositivo",
                    fmt(vatRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:BaseImponibleOimporteNoSujeto", fmt(v[0].setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:CuotaRepercutida", fmt(v[1].setScale(2, RoundingMode.HALF_UP))));
            if (v[2].compareTo(BigDecimal.ZERO) > 0) {
                sb.append(tag("sf:TipoRecargoEquivalencia",
                        fmt(v[2].multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
                sb.append(tag("sf:CuotaRecargoEquivalencia", fmt(v[3].setScale(2, RoundingMode.HALF_UP))));
            }
            sb.append("            </sf:DetalleDesglose>\n");
        }
        sb.append("          </sf:Desglose>\n");
        return sb.toString();
    }

    private String encadenamiento(String prevHash, String nif, String prevNumSerie, String prevFecha) {
        StringBuilder sb = new StringBuilder("          <sf:Encadenamiento>\n");
        boolean esPrimero = VerifactuHashCalculator.INITIAL_HASH.equals(prevHash)
                || prevNumSerie == null || prevFecha == null;
        if (esPrimero) {
            sb.append(tag("sf:PrimerRegistro", "S"));
        } else {
            sb.append("            <sf:RegistroAnterior>\n");
            sb.append(tag("sf:IDEmisorFactura", nif));
            sb.append(tag("sf:NumSerieFactura", prevNumSerie));
            sb.append(tag("sf:FechaExpedicionFactura", prevFecha));
            sb.append(tag("sf:Huella", prevHash));
            sb.append("            </sf:RegistroAnterior>\n");
        }
        sb.append("          </sf:Encadenamiento>\n");
        return sb.toString();
    }

    private String encadenamientoTicket(String prevHash, String nif, String prevNumSerie, String prevFecha) {
        return encadenamiento(prevHash, nif, prevNumSerie, prevFecha);
    }

    private String sistemaInformatico(String nif, String nombre, String softNombre,
            String softId, String version, String instalacion) {
        return "          <sf:SistemaInformatico>\n"
                + tag("sf:NombreRazon", esc(nombre))
                + tag("sf:NIF", nif)
                + tag("sf:NombreSistemaInformatico",
                        softNombre.length() > 30 ? softNombre.substring(0, 30) : softNombre)
                + tag("sf:IdSistemaInformatico", softId.length() > 2 ? softId.substring(0, 2) : softId)
                + tag("sf:Version", version)
                + tag("sf:NumeroInstalacion", instalacion)
                + tag("sf:TipoUsoPosibleSoloVerifactu", "S")
                + tag("sf:TipoUsoPosibleMultiOT", "N")
                + tag("sf:IndicadorMultiplesOT", "N")
                + "          </sf:SistemaInformatico>\n";
    }

    // ================================================================
    // Helpers - encadenamiento anterior (busca el registro previo)
    // ================================================================

    private String getPreviousNumSerie(Invoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice()))
            return null;
        return invoiceRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(Invoice::getInvoiceNumber).orElse(null);
    }

    private String getPreviousFecha(Invoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice()))
            return null;
        return invoiceRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(i -> hashCalculator.getFechaExpedicion(i.getCreatedAt())).orElse(null);
    }

    private String getPreviousTicketNumSerie(Ticket current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice()))
            return null;
        return ticketRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(Ticket::getTicketNumber).orElse(null);
    }

    private String getPreviousTicketFecha(Ticket current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice()))
            return null;
        return ticketRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(t -> hashCalculator.getFechaExpedicion(t.getCreatedAt())).orElse(null);
    }

    private String getPreviousRectNumSerie(RectificativeInvoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice()))
            return null;
        return rectRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(RectificativeInvoice::getRectificativeNumber).orElse(null);
    }

    private String getPreviousRectFecha(RectificativeInvoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice()))
            return null;
        return rectRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(r -> hashCalculator.getFechaExpedicion(r.getCreatedAt())).orElse(null);
    }

    private BigDecimal estimateCuota(SaleReturn saleReturn, Sale originalSale) {
        if (originalSale == null || originalSale.getTotalAmount().compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;
        BigDecimal ratio = saleReturn.getTotalRefunded()
                .divide(originalSale.getTotalAmount().abs(), 10, RoundingMode.HALF_UP);
        return originalSale.getTotalVat().add(originalSale.getTotalRecargo()).multiply(ratio);
    }

    // ================================================================
    // Utilidades
    // ================================================================

    private String tag(String name, String value) {
        return "          <" + name + ">" + value + "</" + name + ">\n";
    }

    private String fmt(BigDecimal v) {
        return v.toPlainString();
    }

    private String esc(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private SubsanarRequest getPuntualData(Sale sale) {
        if (sale == null || sale.getClientePuntualJson() == null || sale.getClientePuntualJson().isBlank()) return null;
        try {
            return objectMapper.readValue(sale.getClientePuntualJson(), SubsanarRequest.class);
        } catch (Exception e) {
            return null;
        }
    }
}
