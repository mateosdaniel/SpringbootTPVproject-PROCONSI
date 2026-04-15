package com.proconsi.electrobazar;

import com.proconsi.electrobazar.dto.AbonoRequest;
import com.proconsi.electrobazar.model.Abono;
import com.proconsi.electrobazar.model.MetodoPagoAbono;
import com.proconsi.electrobazar.model.TipoAbono;
import com.proconsi.electrobazar.service.AbonoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
public class AbonoTestRunner {

    @Autowired
    private AbonoService abonoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testAbonos() {
        System.out.println("\n\n=============== COMIENZO TEST DE ABONOS ===============");

        try {
            Long clienteId;
            try {
                clienteId = jdbcTemplate.queryForObject("SELECT id FROM customers LIMIT 1", Long.class);
            } catch (Exception ex) {
                System.out.println("❌ Error: No se encontró la tabla de clientes (customers) o está vacía.");
                return;
            }

            System.out.println("ℹ️ Utilizando cliente existente en la DB (ID: " + clienteId + ")");

            // 1. Crear Abono
            AbonoRequest request = new AbonoRequest();
            request.setClienteId(String.valueOf(clienteId));
            request.setImporte(new BigDecimal("15.50"));
            request.setMetodoPago(MetodoPagoAbono.EFECTIVO);
            request.setTipoAbono(TipoAbono.COMPENSACION);
            request.setMotivo("Compensación de prueba automática");

            System.out.println("⏳ Peticionando la inserción del Abono...");
            Abono abonoCreado = abonoService.createAbono(request);
            System.out.printf("✅ Abono creado. ID DADO: %d | Importe en DB: %s € | Estado: %s | Tipo: %s%n",
                    abonoCreado.getId(), abonoCreado.getImporte(), abonoCreado.getEstado(), abonoCreado.getTipoAbono());

            // 2. Listar Abonos
            System.out.println("\n⏳ Listando abonos mediante el endpoint Service...");
            List<Abono> abonosCliente = abonoService.getAbonosByCliente(String.valueOf(clienteId));
            System.out.println("✅ El cliente ID: " + clienteId + " tiene actualmente " + abonosCliente.size()
                    + " abonos en el sistema.");

            // 3. Anular Abono
            System.out.println("\n⏳ Llamando al servicio de anulación...");
            abonoService.anularAbono(abonoCreado.getId());

            String nuevoEstado = jdbcTemplate.queryForObject("SELECT estado FROM abonos WHERE id = ?", String.class,
                    abonoCreado.getId());
            System.out.println("✅ Estado validado directamente desde SQL BBDD tras la anulación: " + nuevoEstado);

        } catch (Exception e) {
            System.out.println("❌ ERROR SEVERO EN EJECUCIÓN:");
            e.printStackTrace();
        }

        System.out.println("=============== FIN TEST DE ABONOS ===============\n\n");
    }
}
