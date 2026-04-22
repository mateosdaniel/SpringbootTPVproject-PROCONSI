package com.proconsi.electrobazar.service.verifactu;

import com.proconsi.electrobazar.config.VerifactuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.net.ssl.*;
import javax.xml.parsers.DocumentBuilderFactory;
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

    /** Resultado de un envío a la AEAT. */
    public record AeatResponse(boolean success, String estado, String descripcion) {}

    /**
     * Envía un XML SOAP a la AEAT y devuelve el resultado.
     * Si el certificado no está configurado, devuelve éxito simulado para no bloquear.
     */
    public AeatResponse send(String soapXml) {
        if (!props.isCertificateConfigured()) {
            log.warn("Verifactu: certificado no configurado. Envío simulado (modo prueba sin cert).");
            return new AeatResponse(true, "SimuladoSinCert", "Certificado no configurado todavía");
        }

        try {
            SSLContext sslContext = buildSslContext();
            return doPost(soapXml, sslContext);
        } catch (Exception e) {
            log.error("Verifactu: error enviando a AEAT: {}", e.getMessage(), e);
            return new AeatResponse(false, "ErrorConexion", e.getMessage());
        }
    }

    // ================================================================
    //  HTTP POST
    // ================================================================

    private AeatResponse doPost(String soapXml, SSLContext sslContext) throws Exception {
        URL url = java.net.URI.create(props.getEndpointUrl()).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        conn.setRequestProperty("SOAPAction", "\"\"");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setDoOutput(true);

        log.debug("Verifactu XML enviado:\n{}", soapXml);
        byte[] body = soapXml.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int httpStatus = conn.getResponseCode();
        InputStream is = httpStatus >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String responseBody = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);

        log.debug("Verifactu HTTP {}: {}", httpStatus, responseBody);
        log.info("Verifactu AEAT raw response: {}", responseBody);

        if (httpStatus == 200) {
            return parseResponse(responseBody);
        }
        return new AeatResponse(false, "HTTP_" + httpStatus, responseBody);
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

    private AeatResponse parseResponse(String xml) {
        try {
            log.info("Verifactu parsing response: {}", xml);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Verificamos si hay un error SOAP (Fault)
            String fault = firstText(doc, "faultstring");
            if (fault != null) {
                log.warn("Verifactu AEAT SOAP Fault: {}", fault);
                return new AeatResponse(false, "Fault", fault);
            }

            // EstadoEnvio global
            String estadoEnvio = firstText(doc, "EstadoEnvio");
            if (estadoEnvio == null) estadoEnvio = firstText(doc, "ResultadoEnvio");

            // EstadoRegistro del primer registro (solo enviamos uno a la vez)
            String estadoReg = firstText(doc, "EstadoRegistro");
            String codError = firstText(doc, "CodigoErrorRegistro");
            String descError = firstText(doc, "DescripcionErrorRegistro");

            log.info("EstadoEnvio={}, EstadoRegistro={}, codError={}, descError={}", estadoEnvio, estadoReg, codError, descError);

            boolean ok = "Correcto".equalsIgnoreCase(estadoEnvio)
                    || "Correcto".equalsIgnoreCase(estadoReg)
                    || "AceptadaConErrores".equalsIgnoreCase(estadoReg);

            String estado = estadoReg != null ? estadoReg : estadoEnvio;
            String desc = descError != null ? descError : (codError != null ? "Código: " + codError : "OK");
            return new AeatResponse(ok, estado, desc);

        } catch (Exception e) {
            log.warn("Verifactu: no se pudo parsear la respuesta AEAT: {}", e.getMessage());
            return new AeatResponse(false, "ParseError", e.getMessage());
        }
    }

    private String firstText(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) nodes = doc.getElementsByTagName(localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        return null;
    }
}
