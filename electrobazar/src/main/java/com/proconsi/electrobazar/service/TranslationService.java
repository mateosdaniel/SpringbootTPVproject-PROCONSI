package com.proconsi.electrobazar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for automatic translation.
 * CURRENTLY DEACTIVATED: DeepL integration disabled until API key is provided.
 */
@Slf4j
@Service
public class TranslationService {

    // private final Translator translator; // Commented out to avoid dependency issues if key is missing

    /**
     * Translates text to the specified target language.
     * Currently returns original text since DeepL is disabled.
     */
    public String translate(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        // log.warn("DeepL translation is disabled. Returning original text: {}", text);
        return text;
    }

    /**
     * Translates text and returns the result object.
     * Currently returns original text since DeepL is disabled.
     */
    public TranslationResult translateWithDetection(String text, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return new TranslationResult(null, null);
        }
        
        return new TranslationResult(text, null);
    }

    public record TranslationResult(String text, String detectedLanguage) {}
}
