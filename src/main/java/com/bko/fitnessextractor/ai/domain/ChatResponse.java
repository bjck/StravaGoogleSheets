package com.bko.fitnessextractor.ai.domain;

public record ChatResponse(
        String model,
        String text,
        boolean usedFitnessContext
) {
}

