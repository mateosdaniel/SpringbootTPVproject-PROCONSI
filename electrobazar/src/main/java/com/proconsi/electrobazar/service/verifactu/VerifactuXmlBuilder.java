package com.proconsi.electrobazar.service.verifactu;

import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.RectificativeInvoiceRepository;
import com.proconsi.electrobazar.repository.TicketRepository;
import com.proconsi.electrobazar.util.VerifactuHashCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    // ================================================================
    //  Factura completa (F1)
    // ================================================================

    public String buildAltaInvoice(Invoice invoice, CompanySettings company,
                                   String softwareNombre, String softwareId,
                                   String softwareVersion, String softwareInstalacion) {
        Sale sale = invoice.getSale();
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(invoice.getCreatedAt());
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(invoice.getCreatedAt());

        StringBuilder sb = new StringBuilder();
        sb.append(soapEnvelopeOpen());
        sb.append(regFactuOpen(nif, company.getName()));
        sb.append(registroFacturaOpen());

        sb.append("        <sf:RegistroAlta>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append(idFactura(nif, invoice.getInvoiceNumber(), fechaExp));
        sb.append(tag("sf:NombreRazonEmisor", esc(company.getName())));
        sb.append(tag("sf:TipoFactura", "F1"));
        sb.append(tag("sf:DescripcionOperacion", "Venta TPV " + invoice.getInvoiceNumber()));

        // Destinatario (cliente con NIF)
        if (sale.getCustomer() != null && sale.getCustomer().getTaxId() != null) {
            sb.append("          <sf:Destinatarios>\n");
            sb.append("            <sf:IDDestinatario>\n");
            sb.append(tag("sf:NombreRazon", esc(sale.getCustomer().getName())));
            sb.append(tag("sf:NIF", sale.getCustomer().getTaxId().trim()));
            sb.append("            </sf:IDDestinatario>\n");
            sb.append("          </sf:Destinatarios>\n");
        } else {
            sb.append(tag("sf:FacturaSinIdentifDestinatarioArt61d", "S"));
        }

        sb.append(desglose(sale));

        BigDecimal cuotaTotal = sale.getTotalVat().add(sale.getTotalRecargo()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal importeTotal = sale.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        sb.append(tag("sf:CuotaTotal", fmt(cuotaTotal)));
        sb.append(tag("sf:ImporteTotal", fmt(importeTotal)));

        sb.append(encadenamiento(invoice.getHashPreviousInvoice(), nif,
                getPreviousNumSerie(invoice), getPreviousFecha(invoice)));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre,
                softwareId, softwareVersion, softwareInstalacion));
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", invoice.getHashCurrentInvoice()));
        sb.append("        </sf:RegistroAlta>\n");

        sb.append(registroFacturaClose());
        sb.append(regFactuClose());
        sb.append(soapEnvelopeClose());
        return sb.toString();
    }

    // ================================================================
    //  Factura simplificada (F2) - Ticket
    // ================================================================

    public String buildAltaTicket(Ticket ticket, CompanySettings company,
                                   String softwareNombre, String softwareId,
                                   String softwareVersion, String softwareInstalacion) {
        Sale sale = ticket.getSale();
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(ticket.getCreatedAt());
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(ticket.getCreatedAt());

        StringBuilder sb = new StringBuilder();
        sb.append(soapEnvelopeOpen());
        sb.append(regFactuOpen(nif, company.getName()));
        sb.append(registroFacturaOpen());

        sb.append("        <sf:RegistroAlta>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append(idFactura(nif, ticket.getTicketNumber(), fechaExp));
        sb.append(tag("sf:NombreRazonEmisor", esc(company.getName())));
        sb.append(tag("sf:TipoFactura", "F2"));
        sb.append(tag("sf:DescripcionOperacion", "Venta TPV " + ticket.getTicketNumber()));
        sb.append(tag("sf:FacturaSimplificadaArt7273", "S"));

        sb.append(desglose(sale));

        BigDecimal cuotaTotal = sale.getTotalVat().add(sale.getTotalRecargo()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal importeTotal = sale.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        sb.append(tag("sf:CuotaTotal", fmt(cuotaTotal)));
        sb.append(tag("sf:ImporteTotal", fmt(importeTotal)));

        sb.append(encadenamientoTicket(ticket.getHashPreviousInvoice(), nif,
                getPreviousTicketNumSerie(ticket), getPreviousTicketFecha(ticket)));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre,
                softwareId, softwareVersion, softwareInstalacion));
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", ticket.getHashCurrentInvoice()));
        sb.append("        </sf:RegistroAlta>\n");

        sb.append(registroFacturaClose());
        sb.append(regFactuClose());
        sb.append(soapEnvelopeClose());
        return sb.toString();
    }

    // ================================================================
    //  Factura rectificativa (R1)
    // ================================================================

    public String buildAltaRectificative(RectificativeInvoice rect, CompanySettings company,
                                          String softwareNombre, String softwareId,
                                          String softwareVersion, String softwareInstalacion) {
        SaleReturn saleReturn = rect.getSaleReturn();
        Sale originalSale = saleReturn.getOriginalSale();
        Invoice originalInvoice = rect.getOriginalInvoice();
        String nif = company.getCif();
        String fechaExp = hashCalculator.getFechaExpedicion(rect.getCreatedAt());
        String fechaHoraHuso = hashCalculator.getFechaHoraHuso(rect.getCreatedAt());

        BigDecimal totalRefundedNeg = saleReturn.getTotalRefunded().negate();
        BigDecimal cuotaTotal = estimateCuota(saleReturn, originalSale).negate().setScale(2, RoundingMode.HALF_UP);

        StringBuilder sb = new StringBuilder();
        sb.append(soapEnvelopeOpen());
        sb.append(regFactuOpen(nif, company.getName()));
        sb.append(registroFacturaOpen());

        sb.append("        <sf:RegistroAlta>\n");
        sb.append("          <sf:IDVersion>1.0</sf:IDVersion>\n");
        sb.append(idFactura(nif, rect.getRectificativeNumber(), fechaExp));
        sb.append(tag("sf:NombreRazonEmisor", esc(company.getName())));
        sb.append(tag("sf:TipoFactura", "R1"));
        sb.append(tag("sf:TipoRectificativa", "I"));

        // Referencia a la factura original rectificada
        sb.append("          <sf:FacturasRectificadas>\n");
        sb.append("            <sf:IDFacturaRectificada>\n");
        sb.append(tag("sf:IDEmisorFactura", nif));
        sb.append(tag("sf:NumSerieFactura", originalInvoice.getInvoiceNumber()));
        sb.append(tag("sf:FechaExpedicionFactura",
                hashCalculator.getFechaExpedicion(originalInvoice.getCreatedAt())));
        sb.append("            </sf:IDFacturaRectificada>\n");
        sb.append("          </sf:FacturasRectificadas>\n");

        sb.append(tag("sf:DescripcionOperacion",
                "Rectificación " + originalInvoice.getInvoiceNumber() + ". " + esc(rect.getReason())));

        // Destinatario (del original)
        if (originalSale.getCustomer() != null && originalSale.getCustomer().getTaxId() != null) {
            sb.append("          <sf:Destinatarios>\n");
            sb.append("            <sf:IDDestinatario>\n");
            sb.append(tag("sf:NombreRazon", esc(originalSale.getCustomer().getName())));
            sb.append(tag("sf:NIF", originalSale.getCustomer().getTaxId().trim()));
            sb.append("            </sf:IDDestinatario>\n");
            sb.append("          </sf:Destinatarios>\n");
        }

        sb.append(desgloseRectificativo(saleReturn, originalSale));
        sb.append(tag("sf:CuotaTotal", fmt(cuotaTotal)));
        sb.append(tag("sf:ImporteTotal", fmt(totalRefundedNeg.setScale(2, RoundingMode.HALF_UP))));

        sb.append(encadenamiento(rect.getHashPreviousInvoice(), nif,
                getPreviousRectNumSerie(rect), getPreviousRectFecha(rect)));
        sb.append(sistemaInformatico(nif, company.getName(), softwareNombre,
                softwareId, softwareVersion, softwareInstalacion));
        sb.append(tag("sf:FechaHoraHusoGenRegistro", fechaHoraHuso));
        sb.append(tag("sf:TipoHuella", "01"));
        sb.append(tag("sf:Huella", rect.getHashCurrentInvoice()));
        sb.append("        </sf:RegistroAlta>\n");

        sb.append(registroFacturaClose());
        sb.append(regFactuClose());
        sb.append(soapEnvelopeClose());
        return sb.toString();
    }

    // ================================================================
    //  Helpers - estructura XML
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
            groups.merge(rate, new BigDecimal[]{
                    line.getBaseAmount(), line.getVatAmount(), line.getRecargoRate(), line.getRecargoAmount()
            }, (a, b) -> new BigDecimal[]{
                    a[0].add(b[0]), a[1].add(b[1]), b[2], a[3].add(b[3])
            });
        }
        // Si no hay líneas (caso raro), fallback al total de la venta
        if (groups.isEmpty()) {
            groups.put(BigDecimal.ZERO, new BigDecimal[]{
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
            sb.append(tag("sf:TipoImpositivo", fmt(vatRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:BaseImponibleOimporteNoSujeto", fmt(v[0].setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:CuotaRepercutida", fmt(v[1].setScale(2, RoundingMode.HALF_UP))));
            if (v[2].compareTo(BigDecimal.ZERO) > 0) {
                sb.append(tag("sf:TipoRecargoEquivalencia", fmt(v[2].multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
                sb.append(tag("sf:CuotaRecargoEquivalencia", fmt(v[3].setScale(2, RoundingMode.HALF_UP))));
            }
            sb.append("            </sf:DetalleDesglose>\n");
        }
        sb.append("          </sf:Desglose>\n");
        return sb.toString();
    }

    private String desgloseRectificativo(SaleReturn saleReturn, Sale originalSale) {
        // Construir el desglose negativo a partir de las líneas devueltas y sus SaleLines originales
        Map<BigDecimal, BigDecimal[]> groups = new LinkedHashMap<>();
        for (ReturnLine rl : saleReturn.getLines()) {
            SaleLine sl = rl.getSaleLine();
            if (sl == null) continue;
            BigDecimal ratio = rl.getQuantity().abs()
                    .divide(sl.getQuantity().abs(), 10, RoundingMode.HALF_UP);
            BigDecimal base = sl.getBaseAmount().multiply(ratio).negate();
            BigDecimal vat = sl.getVatAmount().multiply(ratio).negate();
            BigDecimal reRate = sl.getRecargoRate();
            BigDecimal re = sl.getRecargoAmount().multiply(ratio).negate();
            groups.merge(sl.getVatRate(), new BigDecimal[]{base, vat, reRate, re},
                    (a, b) -> new BigDecimal[]{a[0].add(b[0]), a[1].add(b[1]), b[2], a[3].add(b[3])});
        }
        if (groups.isEmpty()) {
            BigDecimal ratio = originalSale.getTotalAmount().compareTo(BigDecimal.ZERO) != 0
                    ? saleReturn.getTotalRefunded().divide(originalSale.getTotalAmount().abs(), 10, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            groups.put(BigDecimal.ZERO, new BigDecimal[]{
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
            sb.append(tag("sf:TipoImpositivo", fmt(vatRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:BaseImponibleOimporteNoSujeto", fmt(v[0].setScale(2, RoundingMode.HALF_UP))));
            sb.append(tag("sf:CuotaRepercutida", fmt(v[1].setScale(2, RoundingMode.HALF_UP))));
            if (v[2].compareTo(BigDecimal.ZERO) > 0) {
                sb.append(tag("sf:TipoRecargoEquivalencia", fmt(v[2].multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))));
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
                + tag("sf:NombreSistemaInformatico", softNombre.length() > 30 ? softNombre.substring(0, 30) : softNombre)
                + tag("sf:IdSistemaInformatico", softId.length() > 2 ? softId.substring(0, 2) : softId)
                + tag("sf:Version", version)
                + tag("sf:NumeroInstalacion", instalacion)
                + tag("sf:TipoUsoPosibleSoloVerifactu", "S")
                + tag("sf:TipoUsoPosibleMultiOT", "N")
                + tag("sf:IndicadorMultiplesOT", "N")
                + "          </sf:SistemaInformatico>\n";
    }

    // ================================================================
    //  Helpers - encadenamiento anterior (busca el registro previo)
    // ================================================================

    private String getPreviousNumSerie(Invoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice())) return null;
        return invoiceRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(Invoice::getInvoiceNumber).orElse(null);
    }

    private String getPreviousFecha(Invoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice())) return null;
        return invoiceRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(i -> hashCalculator.getFechaExpedicion(i.getCreatedAt())).orElse(null);
    }

    private String getPreviousTicketNumSerie(Ticket current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice())) return null;
        return ticketRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(Ticket::getTicketNumber).orElse(null);
    }

    private String getPreviousTicketFecha(Ticket current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice())) return null;
        return ticketRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(t -> hashCalculator.getFechaExpedicion(t.getCreatedAt())).orElse(null);
    }

    private String getPreviousRectNumSerie(RectificativeInvoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice())) return null;
        return rectRepository.findByHashCurrentInvoice(current.getHashPreviousInvoice())
                .map(RectificativeInvoice::getRectificativeNumber).orElse(null);
    }

    private String getPreviousRectFecha(RectificativeInvoice current) {
        if (VerifactuHashCalculator.INITIAL_HASH.equals(current.getHashPreviousInvoice())) return null;
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
    //  Utilidades
    // ================================================================

    private String tag(String name, String value) {
        return "          <" + name + ">" + value + "</" + name + ">\n";
    }

    private String fmt(BigDecimal v) {
        return v.toPlainString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
