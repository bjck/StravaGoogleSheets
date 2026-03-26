package com.bko.fitnessextractor.ai.web.dto;

import java.util.List;

public record ChatResponseDto(
        String model,
        String text,
        boolean usedContext,
        List<String> toolsUsed
) { }
