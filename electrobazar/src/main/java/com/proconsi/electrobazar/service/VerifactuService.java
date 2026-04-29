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

    /** Envía una anulación de factura o ticket a la AEAT de forma asíncrona. */
    void submitAnulacionAsync(Long invoiceId, boolean isTicket);

    /** Reintenta todos los registros en estado PENDING_SEND con reintentos < maxAttempts. */
    void retryPendingSend();

    /** Envía una subsanación de un registro previamente aceptado con errores. */
    void submitSubsanacionAsync(Long id, String type);
}
