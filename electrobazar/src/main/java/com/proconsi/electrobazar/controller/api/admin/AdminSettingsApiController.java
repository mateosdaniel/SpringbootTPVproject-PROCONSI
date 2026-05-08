package com.proconsi.electrobazar.controller.api.admin;

import com.proconsi.electrobazar.model.AppSetting;
import com.proconsi.electrobazar.model.CompanySettings;
import com.proconsi.electrobazar.repository.AppSettingRepository;
import com.proconsi.electrobazar.service.CompanySettingsService;
import com.proconsi.electrobazar.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for managing company and system settings.
 */
@Slf4j
@RestController
@RequestMapping({ "/api/admin", "/admin/api" })
@RequiredArgsConstructor
public class AdminSettingsApiController {

    private final CompanySettingsService companySettingsService;
    private final AppSettingRepository appSettingRepository;
    private final AesEncryptionUtil aesEncryptionUtil;

    @GetMapping("/settings")
    public ResponseEntity<CompanySettings> getSettings() {
        return ResponseEntity.ok(companySettingsService.getSettings());
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody CompanySettings companySettings) {
        companySettingsService.save(companySettings);
        return ResponseEntity.ok(Map.of("message", "Configuración de empresa actualizada correctamente."));
    }

    @GetMapping("/mail-settings")
    public ResponseEntity<Map<String, String>> getMailSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("host", appSettingRepository.findByKey("mail.host").map(AppSetting::getValue).orElse(""));
        settings.put("port", appSettingRepository.findByKey("mail.port").map(AppSetting::getValue).orElse("587"));
        settings.put("username", appSettingRepository.findByKey("mail.username").map(AppSetting::getValue).orElse(""));
        settings.put("password", appSettingRepository.findByKey("mail.password").isPresent() ? "••••••••" : "");
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/mail-settings")
    public ResponseEntity<?> saveMailSettings(@RequestBody Map<String, String> body) {
        if (body.get("host") != null)
            saveAppSetting("mail.host", body.get("host"));
        if (body.get("port") != null)
            saveAppSetting("mail.port", body.get("port"));
        if (body.get("username") != null)
            saveAppSetting("mail.username", body.get("username"));
        if (body.get("password") != null && !body.get("password").isBlank()
                && !body.get("password").equals("••••••••")) {
            saveAppSetting("mail.password", aesEncryptionUtil.encrypt(body.get("password")));
        }
        return ResponseEntity.ok(Map.of("message", "Configuración guardada correctamente"));
    }

    private void saveAppSetting(String key, String value) {
        AppSetting setting = appSettingRepository.findByKey(key)
                .orElse(AppSetting.builder().key(key).build());
        setting.setValue(value);
        appSettingRepository.save(setting);
    }
}
