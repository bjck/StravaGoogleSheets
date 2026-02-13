package com.bko.fitnessextractor.ai.infrastructure.gemini;

import com.bko.fitnessextractor.shared.EnvConfig;
import com.google.genai.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class GeminiConfiguration {

    @Bean
    public GeminiProperties geminiProperties(Environment environment, EnvConfig envConfig) {
        String apiKey = firstNonEmpty(
                environment.getProperty("ai.gemini.api-key"),
                environment.getProperty("AI_GEMINI_API_KEY"),
                environment.getProperty("GEMINI_API_KEY"),
                envConfig.get("ai.gemini.api-key"),
                envConfig.get("gemini.api_key"));

        String defaultModel = firstNonEmpty(
                environment.getProperty("ai.gemini.default-model"),
                "gemini-1.5-flash-latest");

        String baseUrl = firstNonEmpty(
                environment.getProperty("ai.gemini.base-url"),
                "https://generativelanguage.googleapis.com/v1beta");

        return new GeminiProperties(apiKey, defaultModel, baseUrl);
    }

    @Bean
    public Client googleGenAiClient(GeminiProperties properties) {
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            System.setProperty("GOOGLE_API_KEY", properties.apiKey());
        }
        // Client will read GOOGLE_API_KEY env var or system property.
        return new Client();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
