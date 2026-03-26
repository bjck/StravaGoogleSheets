package com.bko.fitnessextractor.smoke;

import com.bko.fitnessextractor.ai.domain.ChatPort;
import com.bko.fitnessextractor.ai.infrastructure.gemini.GeminiProperties;
import com.google.genai.Client;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Replaces the real Gemini client and chat adapter with stubs for testing.
 * This avoids needing a real API key during tests.
 */
@TestConfiguration
public class TestGeminiConfig {

    @Bean
    @Primary
    public GeminiProperties testGeminiProperties() {
        return new GeminiProperties("test-key", "gemini-test", "https://localhost");
    }

    @Bean
    @Primary
    public Client testGoogleGenAiClient() {
        // Set a dummy key so the Client constructor doesn't fail
        System.setProperty("GOOGLE_API_KEY", "test-key-for-smoke-test");
        return new Client();
    }

    @Bean
    @Primary
    public ChatPort testChatPort() {
        return command -> new ChatPort.ChatResult(command.model(), "Test response", List.of());
    }
}
