package com.proconsi.electrobazar.controller.advice;

import com.proconsi.electrobazar.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.proconsi.electrobazar.model.CompanySettings;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CompanySettingsService companySettingsService;

    /**
     * Expose CompanySettings to ALL views automatically.
     * This ensures tags like ${companySettings.appName} work in login.html,
     * index.html, etc., without manually adding them in every controller method.
     */
    @ModelAttribute("companySettings")
    public CompanySettings getCompanySettings() {
        return companySettingsService.getSettings();
    }
}
