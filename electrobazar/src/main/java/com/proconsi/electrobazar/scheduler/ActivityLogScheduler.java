package com.proconsi.electrobazar.scheduler;

import com.proconsi.electrobazar.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Scheduled task for database maintenance of activity logs.
 * Ensures the audit trail doesn't grow indefinitely by purging old records
 * based on their importance (operational vs. audit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLogScheduler {

    private final ActivityLogRepository activityLogRepository;

    /**
     * Daily maintenance of the activity log.
     * Deletes high-volume operational logs older than 90 days.
     * Deletes critical audit logs older than 365 days.
     * 
     * Cron schedule: Runs every day at 02:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupActivityLogs() {
        log.info("Starting Activity Log cleanup task...");

        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        LocalDateTime oneYearAgo = LocalDateTime.now().minusDays(365);

        List<String> operationalActions = Arrays.asList(
                "VENTA",
                "INICIAR_SESION",
                "AJUSTE_STOCK",
                "MODIFICAR_RECARGO"
        );

        try {
            activityLogRepository.deleteByTimestampBeforeAndActionIn(ninetyDaysAgo, operationalActions);
            log.info("Deleted operational logs older than 90 days.");

            activityLogRepository.deleteByTimestampBeforeAndActionNotIn(oneYearAgo, operationalActions);
            log.info("Deleted audit logs older than 365 days.");

            log.info("Activity Log cleanup task completed successfully.");
        } catch (Exception e) {
            log.error("Error during Activity Log cleanup: {}", e.getMessage(), e);
        }
    }
}
