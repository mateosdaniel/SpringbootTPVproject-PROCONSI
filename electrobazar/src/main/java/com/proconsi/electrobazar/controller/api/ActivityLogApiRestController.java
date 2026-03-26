package com.proconsi.electrobazar.controller.api;

import com.proconsi.electrobazar.model.ActivityLog;
import com.proconsi.electrobazar.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for accessing system activity logs.
 * Provides endpoints for administrators to monitor user actions and auditing events.
 */
@RestController
@RequestMapping("/api/activity-log")
public class ActivityLogApiRestController {

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private com.proconsi.electrobazar.service.ActivityLogTranslationService logTranslationService;

    /**
     * Retrieves the most recent activities (limited to a small set).
     * @return List of recent {@link ActivityLog} entries.
     */
    @GetMapping("/recent")
    public List<ActivityLog> getRecent(java.util.Locale locale) {
        List<ActivityLog> logs = activityLogService.getMostRecentActivities();
        return translateLogsIfNeeded(logs, locale);
    }

    /**
     * Retrieves a broader set of recent activity logs.
     * @return List of {@link ActivityLog} entries (typically the last 50).
     */
    @GetMapping
    public List<ActivityLog> getAll(java.util.Locale locale) {
        List<ActivityLog> logs = activityLogService.getRecentActivities();
        return translateLogsIfNeeded(logs, locale);
    }

    private List<ActivityLog> translateLogsIfNeeded(List<ActivityLog> logs, java.util.Locale locale) {
        if (locale.getLanguage().equalsIgnoreCase("es")) {
            // Even if Spanish, we might have legacy English logs in the DB.
            // Let's try to translate them to Spanish for a consistent experience.
            for (ActivityLog log : logs) {
                log.setDescription(logTranslationService.translateToSpanish(log.getDescription()));
            }
            return logs;
        }

        // Translate to English for non-Spanish languages
        for (ActivityLog log : logs) {
            log.setDescription(logTranslationService.translateToEnglish(log.getDescription()));
        }
        return logs;
    }
}
