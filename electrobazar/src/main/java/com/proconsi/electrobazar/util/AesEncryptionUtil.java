package com.proconsi.electrobazar.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for AES encryption and decryption.
 * Uses AES/CBC/PKCS5Padding with a 256-bit key from environment variables.
 */
@Component
public class AesEncryptionUtil {

    private final String secretKey;
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    
    // Static Initialization Vector (16 bytes for AES). 
    // In a more secure implementation, this would be generated randomly and stored with the ciphertext.
    private static final byte[] IV = new byte[16]; 

    public AesEncryptionUtil(@Value("${APP_ENCRYPTION_KEY:}") String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Encrypts plain text using AES-256-CBC.
     * @param plainText The text to encrypt.
     * @return Base64 encoded encrypted string.
     */
    public String encrypt(String plainText) {
        if (plainText == null || secretKey == null || secretKey.length() != 32) {
            return plainText;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Error during encryption: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts AES-256-CBC encrypted text.
     * @param cipherText The Base64 encoded encrypted string.
     * @return Decrypted plain text.
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || secretKey == null || secretKey.length() != 32) {
            return cipherText;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(IV);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Return original text if decryption fails (useful during migration or if not encrypted)
            return cipherText;
        }
    }
}
