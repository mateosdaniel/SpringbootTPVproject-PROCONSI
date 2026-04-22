package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.Sale;
import com.proconsi.electrobazar.model.Ticket;
import com.proconsi.electrobazar.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
public class VerifactuTicketSubmissionTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SaleRepository saleRepository;

    @Test
    public void testTicketCreationAndSubmission() throws Exception {
        // En lugar de buscar una venta existente que podría tener un ticket, creamos una temporal
        Sale sale = new Sale();
        sale.setTotalAmount(new java.math.BigDecimal("111.34"));
        sale.setTotalBase(new java.math.BigDecimal("92.02"));
        sale.setTotalVat(new java.math.BigDecimal("19.32"));
        sale.setTotalRecargo(java.math.BigDecimal.ZERO);
        sale.setTotalDiscount(java.math.BigDecimal.ZERO);
        sale.setReceivedAmount(new java.math.BigDecimal("111.34"));
        sale.setChangeAmount(java.math.BigDecimal.ZERO);
        sale.setPaymentMethod(com.proconsi.electrobazar.model.PaymentMethod.CASH);
        sale = saleRepository.save(sale);

        System.out.println(">>> Iniciando prueba de creación de Ticket para Venta #" + sale.getId());
        
        Ticket ticket = ticketService.createTicket(sale, false);
        System.out.println(">>> Ticket creado: " + ticket.getTicketNumber());
        
        // Esperar un poco a que el hilo asíncrono de Verifactu termine
        System.out.println(">>> Esperando respuesta de AEAT (hilo asíncrono)...");
        Thread.sleep(10000);
        System.out.println(">>> Fin de la prueba.");
    }
}
