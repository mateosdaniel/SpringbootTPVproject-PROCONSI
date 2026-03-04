package com.proconsi.electrobazar.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for validating the operational admin PIN.
 *
 * <p>
 * The PIN is injected exclusively via the {@code ADMIN_PIN} environment
 * variable,
 * resolved through the {@code admin.pin} property in
 * {@code application.properties}.
 * There is no hardcoded default and no runtime fallback; if the environment
 * variable
 * is absent or blank the application will refuse to start.
 * </p>
 *
 * <p>
 * <strong>PIN rotation:</strong> To change the PIN, update the
 * {@code ADMIN_PIN}
 * environment variable and restart the application. The old file-based mutable
 * store
 * has been intentionally removed to avoid secrets being written to disk in
 * plaintext.
 * </p>
 */
@Service
public class AdminPinService {

    private static final Logger log = LoggerFactory.getLogger(AdminPinService.class);

    private final String adminPin;

    public AdminPinService(@Value("${admin.pin}") String adminPin) {
        this.adminPin = adminPin;
    }

    /**
     * Fail-fast validation executed on application startup.
     *
     * <p>
     * Throws {@link IllegalStateException} if {@code ADMIN_PIN} is missing or
     * blank,
     * preventing the application from starting in an insecure, PIN-less state.
     * </p>
     */
    @PostConstruct
    public void validatePinConfiguration() {
        if (adminPin == null || adminPin.isBlank()) {
            throw new IllegalStateException(
                    "[SECURITY] Fatal startup error: the required environment variable " +
                            "'ADMIN_PIN' is not set or is empty. " +
                            "Set the ADMIN_PIN environment variable and restart the application.");
        }
        log.info("[SECURITY] Admin PIN loaded successfully from environment variable ADMIN_PIN.");
    }

    /**
     * Verifies whether the supplied PIN matches the configured admin PIN.
     *
     * @param pin the PIN attempt to verify; {@code null} is treated as an invalid
     *            PIN
     * @return {@code true} if the PIN matches, {@code false} otherwise
     */
    public boolean verifyPin(String pin) {
        if (pin == null || pin.isBlank()) {
            return false;
        }
        return adminPin.equals(pin);
    }
}
