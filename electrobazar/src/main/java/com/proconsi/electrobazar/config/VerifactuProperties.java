package com.proconsi.electrobazar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "verifactu")
@Getter
@Setter
public class VerifactuProperties {

    /** URL base para la verificación del código QR AEAT. */
    private String qrBaseUrl = "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR";

    /** Activar/desactivar el envío a la AEAT. false hasta tener el certificado. */
    private boolean enabled = false;

    /** Entorno AEAT: "pruebas" o "produccion". */
    private String environment = "pruebas";

    private Certificate certificate = new Certificate();
    private Software software = new Software();
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Certificate {
        /** Ruta absoluta al fichero .p12 del certificado digital. */
        private String path;
        /** Contraseña del fichero .p12. */
        private String password;
    }

    @Getter
    @Setter
    public static class Software {
        /** Versión del software TPV declarada ante la AEAT. */
        private String version = "1.0.0";
        /** Identificador de 2 chars del sistema (IdSistemaInformatico). */
        private String idSistema = "EB";
        /** Número de instalación (NumeroInstalacion). */
        private String numeroInstalacion = "1";
    }

    @Getter
    @Setter
    public static class Retry {
        /** Milisegundos entre reintentos del scheduler (defecto 10 min = 600000 ms). */
        private long delayMs = 60_000;
    }

    /** URL de pruebas inyectada manualmente para simular fallos. No se persiste. */
    private String testEndpointUrl;

    public String getEndpointUrl() {
        if (testEndpointUrl != null && !testEndpointUrl.isBlank()) {
            return testEndpointUrl;
        }
        return "pruebas".equalsIgnoreCase(environment)
                ? "https://prewww1.aeat.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"
                : "https://www1.agenciatributaria.gob.es/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP";
    }

    public boolean isCertificateConfigured() {
        return certificate.getPath() != null && !certificate.getPath().isBlank()
                && certificate.getPassword() != null;
    }
}
