package com.bko.fitnessextractor.ai.domain;

public interface ChatPort {
    ChatResult chat(ChatCommand command);

    record ChatCommand(String model, String prompt) {}

    record ChatResult(String model, String text) {}
}

