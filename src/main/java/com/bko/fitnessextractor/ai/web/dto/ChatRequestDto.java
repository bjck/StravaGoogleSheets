package com.bko.fitnessextractor.ai.web.dto;

import java.util.List;

public record ChatRequestDto(
        List<ChatMessageDto> messages,
        Boolean includeContext,
        String model
) { }

