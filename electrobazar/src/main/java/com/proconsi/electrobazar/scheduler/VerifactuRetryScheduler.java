package com.proconsi.electrobazar.scheduler;

import com.proconsi.electrobazar.config.VerifactuProperties;
import com.proconsi.electrobazar.service.VerifactuService;
import jakarta.annotation.PostConstruct;
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

    /** Confirma que el bean ha sido instanciado por Spring correctamente. */
    @PostConstruct
    public void onInit() {
        log.info("Verifactu scheduler bean created. enabled={}, delay-ms configured.", props.isEnabled());
    }

    /** Cada 10 segundos para evaluar rápidamente las condiciones A y B de VeriFactu. */
    @Scheduled(fixedDelayString = "${verifactu.retry.delay-ms:10000}", initialDelayString = "10000")
    public void retryPending() {
        if (!props.isEnabled()) {
            log.debug("Verifactu: scheduler tick — verifactu.enabled=false, skipping.");
            return;
        }
        log.info("Verifactu: Scheduler de reintentos INICIADO.");
        try {
            verifactuService.retryPendingSend();
        } catch (Exception e) {
            log.error("Verifactu: error en scheduler de reintentos: {}", e.getMessage(), e);
        }
    }
}
