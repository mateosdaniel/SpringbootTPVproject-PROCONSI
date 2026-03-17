package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link CompanySettings} entities.
 * Manages the global business configuration (name, address, logo, etc.).
 * Typically used to manage a single configuration record (ID=1).
 */
@Repository
public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {
}
