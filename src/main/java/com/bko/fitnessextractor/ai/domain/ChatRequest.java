package com.bko.fitnessextractor.ai.domain;

import java.util.List;

public record ChatRequest(
        List<ChatMessage> messages,
        boolean includeFitnessContext,
        String model
) {
    public ChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}

