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

    /**
     * Retrieves the most recent activities (limited to a small set).
     * @return List of recent {@link ActivityLog} entries.
     */
    @GetMapping("/recent")
    public List<ActivityLog> getRecent() {
        return activityLogService.getMostRecentActivities();
    }

    /**
     * Retrieves a broader set of recent activity logs.
     * @return List of {@link ActivityLog} entries (typically the last 50).
     */
    @GetMapping
    public List<ActivityLog> getAll() {
        return activityLogService.getRecentActivities();
    }
}
