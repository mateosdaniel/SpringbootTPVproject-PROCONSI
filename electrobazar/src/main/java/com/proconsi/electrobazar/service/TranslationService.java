package com.proconsi.electrobazar.service;

import com.deepl.api.TextResult;
import com.deepl.api.Translator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for automatic translation using DeepL.
 */
@Slf4j
@Service
public class TranslationService {

    @Autowired(required = false)
    private Translator translator;

    /**
     * Translates text to the specified target language.
     * Detects source language automatically.
     * 
     * @param text The text to translate.
     * @param targetLang The target language code (e.g., "ES", "EN-US").
     * @return The translated text, or original text on error.
     */
    public String translate(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        if (translator == null) {
            log.warn("DeepL Translator not initialized. Returning original text.");
            return text;
        }

        try {
            // Adjust "EN" to "EN-US" for DeepL target language if necessary
            String deepLTargetLang = "EN".equalsIgnoreCase(targetLang) ? "EN-US" : targetLang;
            
            TextResult result = translator.translateText(text, null, deepLTargetLang);
            return result.getText();
        } catch (Exception e) {
            log.error("Error during DeepL translation: {}. Returning original text as fallback.", e.getMessage());
            return text;
        }
    }

    /**
     * Translates text and returns the result object containing the detected source language.
     */
    public TranslationResult translateWithDetection(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return new TranslationResult(null, null);
        }

        if (translator == null) {
            return new TranslationResult(text, null);
        }

        try {
            String deepLTargetLang = "EN".equalsIgnoreCase(targetLang) ? "EN-US" : targetLang;
            TextResult result = translator.translateText(text, null, deepLTargetLang);
            return new TranslationResult(result.getText(), result.getDetectedSourceLanguage());
        } catch (Exception e) {
            log.error("Error during DeepL translation with detection: {}", e.getMessage());
            return new TranslationResult(text, null);
        }
    }

    public record TranslationResult(String text, String detectedLanguage) {}
}
