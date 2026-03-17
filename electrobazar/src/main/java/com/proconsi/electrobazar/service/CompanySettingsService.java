package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.CompanySettings;

/**
 * Interface defining operations for company-wide settings management.
 */
public interface CompanySettingsService {

    /**
     * Retrieves the global company configuration (name, address, tax ID, etc.).
     * @return The CompanySettings entity.
     */
    CompanySettings getSettings();

    /**
     * Saves or updates the global company configuration.
     * @param settings The settings to persist.
     * @return The saved CompanySettings.
     */
    CompanySettings save(CompanySettings settings);
}
