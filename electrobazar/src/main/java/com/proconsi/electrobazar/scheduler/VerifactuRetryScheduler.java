package com.proconsi.electrobazar.scheduler;

import com.proconsi.electrobazar.config.VerifactuProperties;
import com.proconsi.electrobazar.service.VerifactuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reintenta periódicamente el envío de registros VeriFactu en estado PENDING_SEND o REJECTED
 * que no hayan superado el máximo de intentos configurado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerifactuRetryScheduler {

    private final VerifactuService verifactuService;
    private final VerifactuProperties props;

    /** Cada minuto por defecto (configurable vía verifactu.retry.delay-ms, por defecto 60000 ms). */
    @Scheduled(fixedDelayString = "${verifactu.retry.delay-ms:60000}", initialDelayString = "60000")
    public void retryPending() {
        log.info("Verifactu: Scheduler de reintentos INICIADO.");
        if (!props.isEnabled()) return;
        try {
            verifactuService.retryPendingSend();
        } catch (Exception e) {
            log.error("Verifactu: error en scheduler de reintentos: {}", e.getMessage(), e);
        }
    }
}
