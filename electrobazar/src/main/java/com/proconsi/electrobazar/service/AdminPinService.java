package com.proconsi.electrobazar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class AdminPinService {

    private final Path pinFilePath;
    private final String defaultPin;

    public AdminPinService(@Value("${admin.pin:1234}") String defaultPin) {
        this.defaultPin = defaultPin;
        // Store the PIN in a file named admin_pin.txt in the application root or data
        // directory
        // Using "admin_pin.txt" in the current working directory for simplicity
        this.pinFilePath = Paths.get("admin_pin.txt");
    }

    public boolean verifyPin(String pin) {
        if (pin == null)
            return false;
        String currentPin = readPin();
        return pin.equals(currentPin);
    }

    public boolean changePin(String oldPin, String newPin) {
        if (!verifyPin(oldPin)) {
            return false;
        }
        if (newPin == null || newPin.trim().isEmpty()) {
            return false;
        }
        try {
            Files.writeString(pinFilePath, newPin.trim(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving new admin PIN: " + e.getMessage());
            return false;
        }
    }

    private String readPin() {
        if (!Files.exists(pinFilePath)) {
            return defaultPin;
        }
        try {
            String pin = Files.readString(pinFilePath).trim();
            return pin.isEmpty() ? defaultPin : pin;
        } catch (IOException e) {
            System.err.println("Error reading admin PIN: " + e.getMessage());
            return defaultPin;
        }
    }
}
