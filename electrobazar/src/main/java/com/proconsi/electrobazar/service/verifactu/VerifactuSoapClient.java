package com.proconsi.electrobazar.service.verifactu;

import com.proconsi.electrobazar.config.VerifactuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.net.ssl.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.List;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

/**
 * Cliente HTTPS para el servicio SOAP AEAT VeriFactu.
 * Usa mTLS: carga el certificado P12 como KeyStore para autenticación de cliente.
 * El servidor AEAT se valida con el TrustStore del sistema (certificado FNMT válido).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerifactuSoapClient {

    private final VerifactuProperties props;

    /** Resultado de una línea en un envío batch. */
    public record AeatLineResponse(String numSerie, String fechaExp, String estadoRegistro, String codError, String descError, String idDuplicado, String estadoDuplicado) {}

    /** Resultado global de un envío a la AEAT. */
    public record AeatBatchResponse(boolean success, String estadoEnvio, String csv, List<AeatLineResponse> lines, String rawResponse, Integer waitTime) {}

    /**
     * Envía un XML SOAP a la AEAT y devuelve el resultado.
     * Si el certificado no está configurado, devuelve éxito simulado para no bloquear.
     */
    public AeatBatchResponse send(String soapXml) {
        if (!props.isCertificateConfigured()) {
            log.warn("Verifactu: certificado no configurado. Envío simulado (modo prueba sin cert).");
            return new AeatBatchResponse(true, "SimuladoSinCert", null, java.util.Collections.emptyList(), null, null);
        }

        try {
            SSLContext sslContext = buildSslContext();
            return doPost(soapXml, sslContext);
        } catch (Exception e) {
            log.error("Verifactu: error enviando a AEAT: {}", e.getMessage(), e);
            return new AeatBatchResponse(false, "ErrorConexion", null, java.util.Collections.emptyList(), null, null);
        }
    }

    // ================================================================
    //  HTTP POST
    // ================================================================

    private AeatBatchResponse doPost(String soapXml, SSLContext sslContext) throws Exception {
        URL url = java.net.URI.create(props.getEndpointUrl()).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        conn.setRequestProperty("SOAPAction", "\"\"");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setDoOutput(true);

        log.trace("Verifactu XML enviado:\n{}", soapXml);
        byte[] body = soapXml.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int httpStatus = conn.getResponseCode();
        InputStream is = httpStatus >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);

        log.trace("Verifactu HTTP {}: {}", httpStatus, responseBody);
        log.trace("Verifactu AEAT raw response: {}", responseBody);

        if (httpStatus == 200) {
            return parseResponse(responseBody);
        }
        return new AeatBatchResponse(false, "HTTP_" + httpStatus, null, java.util.Collections.emptyList(), responseBody, null);
    }

    // ================================================================
    //  SSL / mTLS
    // ================================================================

    private SSLContext buildSslContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] password = props.getCertificate().getPassword().toCharArray();

        try (InputStream certStream = new FileInputStream(props.getCertificate().getPath())) {
            keyStore.load(certStream, password);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        // TrustManagers null = usar el TrustStore del sistema JVM (valida cert AEAT)
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    // ================================================================
    //  Parseo de respuesta AEAT
    // ================================================================

    private AeatBatchResponse parseResponse(String xml) {
        try {
            log.trace("Verifactu parsing batch response: {}", xml);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Verificamos si hay un error SOAP (Fault)
            String fault = firstText(doc, "faultstring");
            if (fault != null) {
                log.warn("Verifactu AEAT SOAP Fault: {}", fault);
                return new AeatBatchResponse(false, "Fault", null, java.util.Collections.emptyList(), xml, null);
            }

            // CSV (Feature 4)
            String csv = firstText(doc, "CSV");

            // EstadoEnvio global
            String estadoEnvio = firstText(doc, "EstadoEnvio");
            if (estadoEnvio == null) estadoEnvio = firstText(doc, "ResultadoEnvio");

            // Parsear cada línea (RespuestaLinea)
            java.util.List<AeatLineResponse> lines = new java.util.ArrayList<>();
            NodeList lineNodes = doc.getElementsByTagNameNS("*", "RespuestaLinea");
            if (lineNodes.getLength() == 0) lineNodes = doc.getElementsByTagName("RespuestaLinea");

            for (int i = 0; i < lineNodes.getLength(); i++) {
                org.w3c.dom.Element el = (org.w3c.dom.Element) lineNodes.item(i);
                
                String numSerie = getText(el, "NumSerieFactura");
                String fecha = getText(el, "FechaExpedicionFactura");
                String estadoReg = getText(el, "EstadoRegistro");
                String codError = getText(el, "CodigoErrorRegistro");
                String descError = getText(el, "DescripcionErrorRegistro");
                
                // Duplicados
                String idDuplicado = getText(el, "IdPeticionRegistroDuplicado");
                String estadoDuplicado = getText(el, "EstadoRegistroDuplicado");

                lines.add(new AeatLineResponse(numSerie, fecha, estadoReg, codError, descError, idDuplicado, estadoDuplicado));
            }

            // TiempoEsperaEnvio o TiempoEspera (específico de VeriFactu)
            String tiempoEsperaStr = firstText(doc, "TiempoEsperaEnvio");
            if (tiempoEsperaStr == null) tiempoEsperaStr = firstText(doc, "TiempoEspera");

            Integer waitTime = null;
            if (tiempoEsperaStr != null) {
                try {
                    waitTime = Integer.parseInt(tiempoEsperaStr);
                } catch (NumberFormatException e) {
                    log.warn("Verifactu: no se pudo parsear TiempoEspera: {}", tiempoEsperaStr);
                }
            }

            boolean ok = "Correcto".equalsIgnoreCase(estadoEnvio) || "ParcialmenteCorrecto".equalsIgnoreCase(estadoEnvio);
            return new AeatBatchResponse(ok, estadoEnvio, csv, lines, xml, waitTime);
        } catch (Exception e) {
            log.warn("Verifactu: no se pudo parsear la respuesta AEAT: {}", e.getMessage());
            return new AeatBatchResponse(false, "ParseError", null, java.util.Collections.emptyList(), xml, null);
        }
    }

    private String getText(org.w3c.dom.Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) nodes = parent.getElementsByTagName(localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        return null;
    }

    private String firstText(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) nodes = doc.getElementsByTagName(localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        return null;
    }
}
