package com.proconsi.electrobazar.controller;

import com.proconsi.electrobazar.model.ActivityLog;
import com.proconsi.electrobazar.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
public class ActivityLogController {

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping
    public List<ActivityLog> getRecentActivity() {
        return activityLogService.getRecentActivities();
    }
}
