package com.bko.fitnessextractor.ai.web.dto;

public record ChatResponseDto(
        String model,
        String text,
        boolean usedContext
) { }

