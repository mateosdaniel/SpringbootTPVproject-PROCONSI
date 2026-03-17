package com.proconsi.electrobazar.advice;

import com.proconsi.electrobazar.model.CompanySettings;
import com.proconsi.electrobazar.service.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Provides global data to all controllers in the application.
 * This component ensures that certain attributes (such as company settings)
 * are available in all views (Thymeleaf) without having to add them manually in each method.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final CompanySettingsService companySettingsService;

    /**
     * Injects company settings into the model of all requests.
     * @return The CompanySettings object with company data (logo, name, etc.)
     */
    @ModelAttribute("companySettings")
    public CompanySettings getCompanySettings() {
        return companySettingsService.getSettings();
    }
}
