package com.proconsi.electrobazar.controller.api.admin;

import com.proconsi.electrobazar.dto.DashboardStatsDTO;
import com.proconsi.electrobazar.model.ActivityLog;
import com.proconsi.electrobazar.service.ActivityLogService;
import com.proconsi.electrobazar.service.CashRegisterService;
import com.proconsi.electrobazar.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST Controller for administrative dashboard, activity logs and bulk imports.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminDashboardApiController {

    private final CashRegisterService cashRegisterService;
    private final ActivityLogService activityLogService;
    private final CsvImportService csvImportService;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(cashRegisterService.getDashboardStats(period));
    }

    @GetMapping("/activity-logs")
    public ResponseEntity<Map<String, Object>> getActivityLogsPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Set<String> allowedSort = Set.of("id", "timestamp", "action", "level", "username");
        String safeSort = allowedSort.contains(sortBy) ? sortBy : "timestamp";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, safeSort));
        org.springframework.data.domain.Slice<ActivityLog> sliceData = activityLogService.getFilteredLogs(search, action, username, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", sliceData.getContent());
        response.put("number", sliceData.getNumber());
        response.put("hasNext", sliceData.hasNext());
        response.put("first", sliceData.isFirst());
        response.put("last", !sliceData.hasNext());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-csv")
    public ResponseEntity<?> uploadCsv(@RequestParam("file") MultipartFile file) throws Exception {
        String result = csvImportService.importProductsCsv(file);
        activityLogService.logActivity("IMPORTAR_CSV", "Importación CSV realizada: " + result, "Admin", "IMPORT", null);
        return ResponseEntity.ok(Map.of("ok", true, "message", result));
    }
}
