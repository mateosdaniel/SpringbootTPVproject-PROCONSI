package com.proconsi.electrobazar.config;

import com.deepl.api.Translator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for DeepL API.
 */
@Configuration
public class DeepLConfig {

    @Value("${deepl.api.key:}")
    private String apiKey;

    @Bean
    public Translator translator() {
        // DeepL API key is currently missing. Returning null to allow app execution.
        /*
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("your_deepl_api_key_here")) {
            return null;
        }
        return new Translator(apiKey);
        */
        return null;
    }
}
