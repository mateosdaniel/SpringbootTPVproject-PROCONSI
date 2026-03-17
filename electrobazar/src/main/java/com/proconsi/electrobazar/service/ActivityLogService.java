package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.ActivityLog;
import com.proconsi.electrobazar.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing and retrieving system activity logs.
 * Provides auditing capabilities by recording user actions.
 */
@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    /**
     * Records a new activity in the log.
     *
     * @param action      The specific action performed (e.g., "CREATE", "DELETE").
     * @param description A human-readable description of the event.
     * @param username    The user who performed the action.
     * @param entityType  The name of the affected entity.
     * @param entityId    The primary key of the affected entity.
     */
    public void logActivity(String action, String description, String username, String entityType, Long entityId) {
        ActivityLog log = ActivityLog.builder()
                .action(action)
                .description(description)
                .username(username != null ? username : "Sistema")
                .entityType(entityType)
                .entityId(entityId)
                .timestamp(LocalDateTime.now())
                .build();
        activityLogRepository.save(log);
    }

    /**
     * Retrieves the 50 most recent activity logs.
     *
     * @return A list of ActivityLog entities ordered by timestamp descending.
     */
    public List<ActivityLog> getRecentActivities() {
        return activityLogRepository.findTop50ByOrderByTimestampDesc();
    }

    /**
     * Retrieves the 20 most recent activity logs for dashboard previews.
     *
     * @return A list of ActivityLog entities.
     */
    public List<ActivityLog> getMostRecentActivities() {
        return activityLogRepository.findTop20ByOrderByTimestampDesc();
    }
}
