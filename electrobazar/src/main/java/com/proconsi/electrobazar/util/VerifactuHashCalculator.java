package com.proconsi.electrobazar.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Calcula la Huella (SHA-256) conforme a la especificación técnica de VeriFactu
 * (Real Decreto 1007/2023 y documentación AEAT).
 *
 * Cadena de entrada (sin separadores):
 *   IDEmisorFactura + NumSerieFactura + FechaExpedicionFactura(dd-MM-yyyy)
 *   + TipoFactura + CuotaTotal(2dec) + ImporteTotal(2dec)
 *   + HuellaAnterior + FechaHoraHusoGenRegistro(ISO-8601)
 */
@Component
public class VerifactuHashCalculator {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static final String INITIAL_HASH = "0000000000000000";

    /**
     * Calcula la Huella Verifactu.
     *
     * @param nif            NIF/CIF emisor (9 chars)
     * @param numSerie       Número de serie de la factura
     * @param fechaHora      Fecha/hora de generación del registro
     * @param tipoFactura    F1, F2, R1, R4, etc.
     * @param cuotaTotal     Suma de IVA + Recargo de equivalencia
     * @param importeTotal   Importe total de la factura
     * @param huellaAnterior Hash del registro anterior (o INITIAL_HASH si es el primero)
     * @return Huella SHA-256 en hexadecimal mayúsculas (64 chars)
     */
    public String calculate(String nif, String numSerie, LocalDateTime fechaHora,
                            String tipoFactura, BigDecimal cuotaTotal,
                            BigDecimal importeTotal, String huellaAnterior) {
        ZonedDateTime zdt = fechaHora.atZone(MADRID);
        String fecha = zdt.format(DATE_FMT);
        String fechaHoraHuso = zdt.format(DATETIME_FMT);

        String input = nif
                + numSerie
                + fecha
                + tipoFactura
                + cuotaTotal.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + importeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + huellaAnterior
                + fechaHoraHuso;

        return sha256Hex(input);
    }

    /** Devuelve FechaExpedicionFactura en formato dd-MM-yyyy para el XML. */
    public String getFechaExpedicion(LocalDateTime fechaHora) {
        return fechaHora.atZone(MADRID).format(DATE_FMT);
    }

    /** Devuelve FechaHoraHusoGenRegistro en formato ISO-8601 para el XML y el hash. */
    public String getFechaHoraHuso(LocalDateTime fechaHora) {
        return fechaHora.atZone(MADRID).format(DATETIME_FMT);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Error calculando Huella Verifactu", e);
        }
    }
}
