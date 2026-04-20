package com.proconsi.electrobazar.service;

/**
 * Servicio principal de VeriFactu.
 * Gestiona el envío asíncrono de registros de facturación a la AEAT
 * y el reintento automático de los registros pendientes.
 */
public interface VerifactuService {

    /** Envía una factura (F1) a la AEAT de forma asíncrona. */
    void submitInvoiceAsync(Long invoiceId);

    /** Envía un ticket (F2) a la AEAT de forma asíncrona. */
    void submitTicketAsync(Long ticketId);

    /** Envía una factura rectificativa (R1) a la AEAT de forma asíncrona. */
    void submitRectificativeAsync(Long rectId);

    /** Reintenta todos los registros en estado PENDING_SEND con reintentos < maxAttempts. */
    void retryPendingSend();
}
