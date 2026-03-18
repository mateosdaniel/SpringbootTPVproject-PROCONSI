package com.proconsi.electrobazar.service;

import com.proconsi.electrobazar.model.AppSetting;
import com.proconsi.electrobazar.repository.AppSettingRepository;
import com.proconsi.electrobazar.util.AesEncryptionUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for validating and managing the operational admin PIN.
 *
 * <p>
 * The PIN is primarily stored in the database.
 * On startup, if no PIN is found, it falls back to the ADMIN_PIN environment
 * variable and seeds it into the database for future updates.
 * </p>
 */
@Service
public class AdminPinService {

    private static final Logger log = LoggerFactory.getLogger(AdminPinService.class);
    private static final String PIN_KEY = "admin_pin";

    private final AppSettingRepository appSettingRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final String fallbackPin;

    public AdminPinService(AppSettingRepository appSettingRepository,
            AesEncryptionUtil aesEncryptionUtil,
            @Value("${admin.pin:}") String fallbackPin) {
        this.appSettingRepository = appSettingRepository;
        this.aesEncryptionUtil = aesEncryptionUtil;
        this.fallbackPin = (fallbackPin == null || fallbackPin.isBlank()) ? "12345" : fallbackPin;
    }

    /**
     * Initial seeding executed on application startup.
     * Seeds the database with the fallback PIN if none is found.
     */
    @PostConstruct
    @Transactional
    public void validateAndSeedPin() {
        if (appSettingRepository.findByKey(PIN_KEY).isEmpty()) {
            log.info("[SECURITY] No initial PIN found in database. Seeding with encrypted fallback value.");
            String encryptedPin = aesEncryptionUtil.encrypt(fallbackPin);
            appSettingRepository.save(AppSetting.builder().key(PIN_KEY).value(encryptedPin).build());
        } else {
            log.info("[SECURITY] Admin PIN configuration loaded from database.");
        }
    }

    /**
     * Retrieves the current admin PIN from the database.
     */
    private String getCurrentPin() {
        return appSettingRepository.findByKey(PIN_KEY)
                .map(setting -> aesEncryptionUtil.decrypt(setting.getValue()))
                .orElse(fallbackPin);
    }

    /**
     * Verifies whether the supplied PIN matches the configured admin PIN.
     *
     * @param pin The PIN attempt to verify.
     * @return true if matches, false otherwise.
     */
    public boolean verifyPin(String pin) {
        if (pin == null || pin.isBlank()) {
            return false;
        }
        return getCurrentPin().equals(pin);
    }

    /**
     * Updates the admin PIN in the database after validating the current one.
     *
     * @param currentPin The current PIN for validation.
     * @param newPin     The new PIN to set.
     * @throws IllegalArgumentException If validation fails.
     */
    @Transactional
    public void updatePin(String currentPin, String newPin) {
        if (!verifyPin(currentPin)) {
            throw new IllegalArgumentException("El PIN actual es incorrecto.");
        }
        if (newPin == null || newPin.length() < 4) {
            throw new IllegalArgumentException("El nuevo PIN debe tener al menos 4 caracteres.");
        }

        AppSetting setting = appSettingRepository.findByKey(PIN_KEY)
                .orElse(AppSetting.builder().key(PIN_KEY).build());
        setting.setValue(aesEncryptionUtil.encrypt(newPin));
        appSettingRepository.save(setting);
        log.info("[SECURITY] Admin PIN updated successfully.");
    }
}
