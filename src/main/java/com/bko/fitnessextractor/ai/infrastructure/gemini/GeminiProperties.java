package com.bko.fitnessextractor.ai.infrastructure.gemini;

public record GeminiProperties(
        String apiKey,
        String defaultModel,
        String baseUrl
) { }
