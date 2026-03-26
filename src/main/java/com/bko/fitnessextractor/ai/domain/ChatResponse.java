package com.bko.fitnessextractor.ai.domain;

import java.util.List;

public record ChatResponse(
        String model,
        String text,
        boolean usedFitnessContext,
        List<String> toolsUsed
) {
    public ChatResponse(String model, String text, boolean usedFitnessContext) {
        this(model, text, usedFitnessContext, List.of());
    }
}
