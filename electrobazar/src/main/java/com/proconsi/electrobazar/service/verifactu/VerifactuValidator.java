package com.proconsi.electrobazar.service.verifactu;

import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.util.NifCifValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Validador de negocio para registros VeriFactu antes de su envío a la AEAT.
 * Evita rechazos técnicos por formatos incorrectos o inconsistencias de datos.
 */
@Component
@RequiredArgsConstructor
public class VerifactuValidator {

    private final NifCifValidator nifValidator;

    public void validateInvoice(Invoice invoice, CompanySettings company) throws ValidationException {
        validateCommon(company);
        validateDocumentNumber(invoice.getInvoiceNumber());
        validateDate(invoice.getCreatedAt());

        Sale sale = invoice.getSale();
        if (sale == null) throw new ValidationException("Factura sin datos de venta");

        validateTaxId(company.getCif(), true);
        if (sale.getCustomer() != null && sale.getCustomer().getTaxId() != null && !sale.getCustomer().getTaxId().isBlank()) {
            validateTaxId(sale.getCustomer().getTaxId(), false);
            validateField("Nombre cliente", sale.getCustomer().getName(), 120, false);
        }

        validateAmounts(sale);
    }

    public void validateTicket(Ticket ticket, CompanySettings company) throws ValidationException {
        validateCommon(company);
        validateDocumentNumber(ticket.getTicketNumber());
        validateDate(ticket.getCreatedAt());

        Sale sale = ticket.getSale();
        if (sale == null) throw new ValidationException("Ticket sin datos de venta");

        validateTaxId(company.getCif(), true);
        validateAmounts(sale);
    }

    public void validateRectificative(RectificativeInvoice rect, CompanySettings company) throws ValidationException {
        validateCommon(company);
        validateDocumentNumber(rect.getRectificativeNumber());
        validateDate(rect.getCreatedAt());

        SaleReturn saleReturn = rect.getSaleReturn();
        if (saleReturn == null) throw new ValidationException("Rectificativa sin datos de devolución");

        validateTaxId(company.getCif(), true);
        validateField("Motivo rectificación", rect.getReason(), 500, false);

        // Validar importes (en rectificativas son usualmente negativos, pero el esquema permite signo)
        // Solo validamos decimales y tipos impositivos
        for (ReturnLine rl : saleReturn.getLines()) {
            SaleLine sl = rl.getSaleLine();
            if (sl != null) validateVatRate(sl.getVatRate());
        }
    }

    private void validateCommon(CompanySettings company) throws ValidationException {
        validateField("Nombre empresa", company.getName(), 120, false);
        validateTaxId(company.getCif(), true);
    }

    private void validateTaxId(String taxId, boolean isEmisor) throws ValidationException {
        if (taxId == null || taxId.isBlank()) {
            throw new ValidationException(isEmisor ? "NIF emisor obligatorio" : "NIF destinatario obligatorio");
        }
        String clean = taxId.trim().toUpperCase();
        
        // Especial Verifactu: aceptar test NIFs starting with 9999
        if ( clean.startsWith("9999") ) return;

        if (!nifValidator.isValid(clean)) {
            throw new ValidationException("Formato de NIF incorrecto: " + clean);
        }
        if (clean.length() != 9) {
            throw new ValidationException("El NIF debe tener exactamente 9 caracteres: " + clean);
        }
    }

    private void validateDocumentNumber(String num) throws ValidationException {
        if (num == null || num.isBlank()) throw new ValidationException("Número de documento vacío");
        if (num.length() > 60) throw new ValidationException("Número de documento excede 60 caracteres");
        if (!isOnlyAscii32_126(num)) throw new ValidationException("Número de documento contiene caracteres no permitidos (solo ASCII 32-126)");
    }

    private void validateDate(LocalDateTime date) throws ValidationException {
        if (date == null) throw new ValidationException("Fecha nula");
        LocalDateTime now = LocalDateTime.now();
        if (date.isAfter(now.plusHours(1))) { // Margen de 1h por desajustes reloj
            throw new ValidationException("La fecha no puede ser futura");
        }
        if (date.isBefore(now.minusYears(4))) {
            throw new ValidationException("La fecha no puede tener más de 4 años de antigüedad");
        }
    }

    private void validateAmounts(Sale sale) throws ValidationException {
        if (sale.getTotalAmount() == null) throw new ValidationException("Importe total nulo");
        
        // AEAT Error 2011: ImporteTotal o CuotaTotal no pueden ser excesivamente grandes o mal formados
        // Verificamos decimales
        validateDecimals("Importe Total", sale.getTotalAmount());
        validateDecimals("Cuota Total", sale.getTotalVat().add(sale.getTotalRecargo()));

        if (sale.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
            // Permitimos bases 0 si hay líneas? Verifactu suele rechazar facturas "vacías"
            if (sale.getLines().isEmpty()) throw new ValidationException("Venta sin líneas");
        }

        for (SaleLine line : sale.getLines()) {
            validateVatRate(line.getVatRate());
            validateDecimals("Precio línea", line.getUnitPrice());
        }
    }

    private void validateVatRate(BigDecimal rate) throws ValidationException {
        if (rate == null) throw new ValidationException("Tipo impositivo nulo");
        // Tipos vigentes en España: 0, 0.04, 0.05, 0.10, 0.21
        double r = rate.doubleValue();
        if (r != 0 && r != 0.04 && r != 0.05 && r != 0.10 && r != 0.21) {
            throw new ValidationException("Tipo impositivo no válido para AEAT: " + (r * 100) + "%");
        }
    }

    private void validateDecimals(String field, BigDecimal val) throws ValidationException {
        if (val == null) return;
        if (val.scale() > 2) {
            // Si el valor tiene más de 2 decimales pero los extra son 0, es aceptable tras setScale
            if (val.setScale(2, java.math.RoundingMode.HALF_UP).compareTo(val) != 0) {
                throw new ValidationException("El campo " + field + " excede el máximo de 2 decimales permitidos: " + val);
            }
        }
    }

    private void validateField(String field, String val, int maxLen, boolean checkAscii) throws ValidationException {
        if (val == null) return;
        if (val.length() > maxLen) {
            throw new ValidationException(field + " excede el máximo de " + maxLen + " caracteres");
        }
        if (checkAscii && !isOnlyAscii32_126(val)) {
            throw new ValidationException(field + " contiene caracteres no permitidos (solo ASCII 32-126)");
        }
    }

    private boolean isOnlyAscii32_126(String s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) return false;
        }
        return true;
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) { super(message); }
    }
}
