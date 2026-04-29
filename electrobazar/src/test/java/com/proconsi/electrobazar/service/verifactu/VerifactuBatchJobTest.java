package com.proconsi.electrobazar.service.verifactu;

import com.proconsi.electrobazar.config.VerifactuProperties;
import com.proconsi.electrobazar.model.*;
import com.proconsi.electrobazar.repository.InvoiceRepository;
import com.proconsi.electrobazar.repository.RectificativeInvoiceRepository;
import com.proconsi.electrobazar.repository.TicketRepository;
import com.proconsi.electrobazar.repository.CompanySettingsRepository;
import com.proconsi.electrobazar.repository.CustomerRepository;
import com.proconsi.electrobazar.repository.SaleRepository;
import com.proconsi.electrobazar.service.impl.VerifactuServiceImpl;
import com.proconsi.electrobazar.util.VerifactuHashCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para la lógica de control de flujo del Worker Veri*Factu.
 *
 * Escenarios cubiertos:
 *  - TEST 1: Bug actual — factura bloqueada por cooldown activo.
 *  - TEST 2: Envío por tiempo cumplido (Condición B).
 *  - TEST 3: Envío inmediato por lote máximo de 1.000 (Condición A, ignora cooldown).
 *
 * Estrategia de mocking:
 *  - LENIENT: evita UnnecessaryStubbingException en setUp compartido;
 *    los tests de bloqueo no usan todos los stubs definidos.
 *  - VerifactuState se usa como @Spy (instancia real) para permitir setters directos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Veri*Factu – Control de flujo AEAT (Batching y Cooldown)")
class VerifactuBatchJobTest {

    // =========================================================
    //  MOCKS — no se hace ninguna petición real a internet ni BD
    // =========================================================

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private RectificativeInvoiceRepository rectRepository;
    @Mock private CompanySettingsRepository companySettingsRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private VerifactuXmlBuilder xmlBuilder;
    @Mock private VerifactuSoapClient soapClient;
    @Mock private VerifactuHashCalculator hashCalculator;
    @Mock private VerifactuValidator validator;
    @Mock private VerifactuProperties props;
    @Mock private ObjectMapper objectMapper;

    /**
     * VerifactuState como @Spy: instancia real wrapeada para que @InjectMocks
     * la inyecte en el servicio, permitiéndonos llamar setters directamente.
     */
    @Spy
    private VerifactuState verifactuState = new VerifactuState();

    @InjectMocks
    private VerifactuServiceImpl verifactuService;

    // Captura cualquier excepción que sendBatch puede silenciar con su try/catch
    private final AtomicReference<Exception> capturedBatchException = new AtomicReference<>();

    // =========================================================
    //  SETUP — stubs comunes a todos los tests
    // =========================================================

    @BeforeEach
    void setUp() {
        // Props: Verifactu habilitado, software configurado
        when(props.isEnabled()).thenReturn(true);
        VerifactuProperties.Software software = new VerifactuProperties.Software();
        when(props.getSoftware()).thenReturn(software);

        // CompanySettings mínimas (CIF + nombre requeridos para buildSoftwareNombre)
        CompanySettings company = new CompanySettings();
        company.setCif("B12345678");
        company.setName("ElectroBazar SL");
        company.setAppName("ElectroBazar");
        when(companySettingsRepository.findById(1L)).thenReturn(Optional.of(company));

        // Tickets y Rectificativas vacías por defecto
        // NOTA: findPendingSend() es un método default → stub directo sobre él
        when(ticketRepository.findPendingSend()).thenReturn(Collections.emptyList());
        when(rectRepository.findPendingSend()).thenReturn(Collections.emptyList());

        // XmlBuilder genera un XML mínimo válido (lo que retorna no importa para estos tests)
        when(xmlBuilder.buildBatch(anyList(), any(), any(), any(), any(), any()))
                .thenReturn("<soap:Envelope><soap:Body><batch/></soap:Body></soap:Envelope>");

        // SoapClient devuelve respuesta de éxito con 60s de nuevo wait-time
        when(soapClient.send(anyString()))
                .thenReturn(new VerifactuSoapClient.AeatBatchResponse(
                        true, "Correcto", "CSV-TEST-001",
                        Collections.emptyList(), "<resp/>", 60));

        // Estado de cooldown limpio al inicio de cada test
        verifactuState.setLastSendTime(null);
        verifactuState.setCurrentWaitSeconds(60);

        capturedBatchException.set(null);
    }

    // =========================================================
    //  HELPER — stub mínimo de Invoice elegible para reintento
    // =========================================================

    private Invoice stubInvoice(int index) {
        Invoice inv = new Invoice();
        inv.setInvoiceNumber("F-2026-" + index);
        inv.setAeatStatus(AeatStatus.PENDING_SEND);
        inv.setAeatRetryCount(0);
        // submissionDate = null → isReadyForRetry devuelve true (nunca enviada)
        inv.setAeatSubmissionDate(null);
        // rejectionReason = null → isEligibleForAutoRetry devuelve true
        inv.setAeatRejectionReason(null);
        inv.setHashPreviousInvoice(VerifactuHashCalculator.INITIAL_HASH);
        inv.setHashCurrentInvoice("HASH-" + index);
        Sale sale = new Sale();
        sale.setTotalAmount(BigDecimal.TEN);
        sale.setTotalBase(new BigDecimal("8.26"));
        sale.setTotalVat(new BigDecimal("1.74"));
        sale.setTotalRecargo(BigDecimal.ZERO);
        sale.setLines(Collections.emptyList());
        inv.setSale(sale);
        return inv;
    }

    // =========================================================
    //  TEST 1 — Bug actual: bloqueo por cooldown activo
    // =========================================================

    /**
     * REPRODUCE EL BUG:
     * Con 1 factura en cola y cooldown activo (10s de 60s transcurridos),
     * el Job NO debe hacer ningún envío SOAP. Ninguna de las dos condiciones
     * (A o B) está cumplida.
     */
    @Test
    @DisplayName("TEST 1 [BUG] — 1 factura + cooldown activo (10s/60s) → 0 envíos SOAP")
    void test1_cooldownActivo_bloqueaElEnvio() {
        // ARRANGE: último envío hace 10s, TiempoEsperaEnvio = 60s → restan 50s → cooldown ACTIVO
        verifactuState.setLastSendTime(LocalDateTime.now().minusSeconds(10));
        verifactuState.setCurrentWaitSeconds(60);

        // 1 sola factura en la cola
        // IMPORTANTE: stubeamos findPendingSend() directamente (es un método default de la interfaz)
        when(invoiceRepository.findPendingSend())
                .thenReturn(List.of(stubInvoice(1)));

        // ACT: tick del Worker
        verifactuService.retryPendingSend();

        // ASSERT
        // Condición A: 1 < 1.000 → NO aplica
        // Condición B: 10s < 60s → cooldown activo → NO aplica
        // ∴ zero llamadas al cliente SOAP
        verify(soapClient, never()).send(anyString());

        // La factura no cambia de estado en la BD local
        verify(invoiceRepository, never()).save(any(Invoice.class));

        // Estado del cooldown sigue activo y consistente
        assertThat(verifactuState.isCooldownActive())
                .as("Cooldown debe seguir activo tras el tick")
                .isTrue();

        assertThat(verifactuState.getRemainingSeconds())
                .as("Deben quedar ~50 segundos de cooldown")
                .isBetween(48L, 52L);
    }

    // =========================================================
    //  TEST 2 — Condición B: tiempo de espera agotado
    // =========================================================

    /**
     * CONDICIÓN B:
     * Con 5 facturas en cola y el cooldown expirado (65s > 60s),
     * el Job debe agrupar todas las facturas en 1 único XML y hacer
     * exactamente 1 llamada SOAP (no 5 llamadas separadas).
     */
    @Test
    @DisplayName("TEST 2 — 5 facturas + 65s transcurridos (cooldown 60s expirado) → 1 llamada SOAP con lote de 5")
    void test2_tiempoCumplido_enviaLoteParcial() {
        // ARRANGE: último envío hace 65s, TiempoEspera = 60s → cooldown EXPIRADO
        verifactuState.setLastSendTime(LocalDateTime.now().minusSeconds(65));
        verifactuState.setCurrentWaitSeconds(60);

        // 5 facturas en cola (< 1.000 → Condición A no aplica)
        List<Invoice> cola = new ArrayList<>();
        IntStream.rangeClosed(1, 5).forEach(i -> cola.add(stubInvoice(i)));
        when(invoiceRepository.findPendingSend()).thenReturn(cola);

        // ACT
        verifactuService.retryPendingSend();

        // ASSERT: el XML builder recibe las 5 facturas en un único lote
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(xmlBuilder, times(1))
                .buildBatch(batchCaptor.capture(), any(), any(), any(), any(), any());

        assertThat(batchCaptor.getValue())
                .as("Las 5 facturas deben agruparse en 1 único lote XML")
                .hasSize(5);

        // El cliente SOAP se invoca 1 sola vez con ese XML (no 5 peticiones separadas)
        verify(soapClient, times(1)).send(anyString());

        // El timestamp de último envío se renueva tras el envío satisfactorio
        assertThat(verifactuState.getLastSendTime())
                .as("lastSendTime debe actualizarse tras el envío")
                .isAfter(LocalDateTime.now().minusSeconds(5));
    }

    // =========================================================
    //  TEST 3 — Condición A: lote de 1.000 máximo (ignora cooldown)
    // =========================================================

    /**
     * CONDICIÓN A (excepción a la regla):
     * Con 1.500 facturas en cola, el Job debe ignorar completamente el cooldown
     * activo (solo 2s transcurridos de 60s), tomar las primeras 1.000 facturas
     * en orden FIFO, enviarlas en 1 único lote SOAP, y dejar las 500 restantes
     * para el siguiente ciclo del scheduler.
     */
    @Test
    @DisplayName("TEST 3 — 1.500 facturas + cooldown activo (2s/60s) → Condición A: envía 1.000 inmediatamente, deja 500")
    void test3_loteLleno_ignoraCooldownEnvia1000() {
        // ARRANGE: cooldown MUY activo (2s de 60s) — sin Condición A nunca enviaría
        verifactuState.setLastSendTime(LocalDateTime.now().minusSeconds(2));
        verifactuState.setCurrentWaitSeconds(60);

        // 1.500 facturas en cola (≥ 1.000 → Condición A activa)
        List<Invoice> cola = new ArrayList<>();
        IntStream.rangeClosed(1, 1500).forEach(i -> cola.add(stubInvoice(i)));
        when(invoiceRepository.findPendingSend()).thenReturn(cola);

        // ACT
        verifactuService.retryPendingSend();

        // ASSERT: el XML builder se llama 1 sola vez con exactamente 1.000 registros
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(xmlBuilder, times(1))
                .buildBatch(batchCaptor.capture(), any(), any(), any(), any(), any());

        List<Object> batchEnviado = batchCaptor.getValue();
        assertThat(batchEnviado)
                .as("Condición A: el lote debe tener exactamente 1.000 registros (límite AEAT)")
                .hasSize(1000);

        // 1 única llamada SOAP — las 500 restantes se dejan para el siguiente tick
        verify(soapClient, times(1)).send(anyString());

        // Verificación del orden FIFO: los 1.000 primeros de la cola
        Invoice primeraDeLote = (Invoice) batchEnviado.get(0);
        assertThat(primeraDeLote.getInvoiceNumber())
                .as("El primer registro del lote debe ser F-2026-1 (FIFO)")
                .isEqualTo("F-2026-1");

        Invoice ultimaDeLote = (Invoice) batchEnviado.get(999);
        assertThat(ultimaDeLote.getInvoiceNumber())
                .as("El registro 1.000 del lote debe ser F-2026-1000")
                .isEqualTo("F-2026-1000");
    }
}
