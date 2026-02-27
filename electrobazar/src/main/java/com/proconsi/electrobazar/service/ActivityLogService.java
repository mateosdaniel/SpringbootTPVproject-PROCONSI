package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.ActivityLog;
import com.proconsi.electrobazar.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

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

    public List<ActivityLog> getRecentActivities() {
        return activityLogRepository.findTop50ByOrderByTimestampDesc();
    }
}
