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
 * IDEmisorFactura + NumSerieFactura + FechaExpedicionFactura(dd-MM-yyyy)
 * + TipoFactura + CuotaTotal(2dec) + ImporteTotal(2dec)
 * + HuellaAnterior + FechaHoraHusoGenRegistro(ISO-8601)
 */
@Component
public class VerifactuHashCalculator {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public static final String INITIAL_HASH = "";

    /**
     * Calcula la Huella Verifactu.
     * ...
     */
    public String calculate(String nif, String numSerie, LocalDateTime fechaHora,
            String tipoFactura, BigDecimal cuotaTotal,
            BigDecimal importeTotal, String huellaAnterior) {
        ZonedDateTime zdt = fechaHora.atZone(MADRID);
        String fecha = zdt.format(DATE_FMT);
        String fechaHoraHuso = zdt.format(DATETIME_FMT);

        // Formato exacto AEAT: field=value&field=value...
        String input = String.format(
                "IDEmisorFactura=%s&NumSerieFactura=%s&FechaExpedicionFactura=%s&" +
                        "TipoFactura=%s&CuotaTotal=%s&ImporteTotal=%s&Huella=%s&FechaHoraHusoGenRegistro=%s",
                nif, numSerie, fecha, tipoFactura,
                cuotaTotal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                importeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                huellaAnterior, fechaHoraHuso);

        return sha256Hex(input);
    }

    public String calculate(String nif, String numSerie, LocalDateTime fechaExpedicion,
            String tipoFactura, BigDecimal cuotaTotal,
            BigDecimal importeTotal, String huellaAnterior,
            String fechaHoraHusoOverride) {
        ZonedDateTime zdt = fechaExpedicion.atZone(MADRID);
        String fecha = zdt.format(DATE_FMT);

        String input = String.format(
                "IDEmisorFactura=%s&NumSerieFactura=%s&FechaExpedicionFactura=%s&" +
                        "TipoFactura=%s&CuotaTotal=%s&ImporteTotal=%s&Huella=%s&FechaHoraHusoGenRegistro=%s",
                nif, numSerie, fecha, tipoFactura,
                cuotaTotal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                importeTotal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                huellaAnterior, fechaHoraHusoOverride);

        return sha256Hex(input);
    }

    /** Devuelve FechaExpedicionFactura en formato dd-MM-yyyy para el XML. */
    public String getFechaExpedicion(LocalDateTime fechaHora) {
        return fechaHora.atZone(MADRID).format(DATE_FMT);
    }

    /**
     * Devuelve FechaHoraHusoGenRegistro en formato ISO-8601 para el XML y el hash.
     */
    public String getFechaHoraHuso(LocalDateTime fechaHora) {
        return fechaHora.atZone(MADRID).format(DATETIME_FMT);
    }

    public String getFechaGen(LocalDateTime fechaHora) {
        return fechaHora.atZone(MADRID).format(DATE_FMT);
    }

    public String getHoraGen(LocalDateTime fechaHora) {
        return fechaHora.atZone(MADRID).format(TIME_FMT);
    }

    public String getHusoGen(LocalDateTime fechaHora) {
        String offset = fechaHora.atZone(MADRID).getOffset().getId();
        return "Z".equals(offset) ? "+00:00" : offset;
    }

    public String calculateAnulacionHash(String nif, String numSerie, LocalDateTime fechaExpedicion,
            String huellaAnterior, String fechaHoraHusoGen) {
        String fecha = getFechaExpedicion(fechaExpedicion);
        String input = String.format(
                "IDEmisorFacturaAnulada=%s&NumSerieFacturaAnulada=%s&FechaExpedicionFacturaAnulada=%s&" +
                        "Huella=%s&FechaHoraHusoGenRegistro=%s",
                nif, numSerie, fecha, huellaAnterior, fechaHoraHusoGen);

        return sha256Hex(input);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1)
                    hex.append('0');
                hex.append(h);
            }
            return hex.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Error calculando Huella Verifactu", e);
        }
    }
}
