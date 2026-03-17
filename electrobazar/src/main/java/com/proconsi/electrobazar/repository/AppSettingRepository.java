package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link AppSetting} entities.
 * Handles persistent key-value configuration for the application.
 */
@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

    /**
     * Retrieves a configuration setting by its unique string key.
     * @param key The setting identifier.
     * @return Optional containing the setting if found.
     */
    Optional<AppSetting> findByKey(String key);
}
