package com.bko.fitnessextractor.ai.domain;

import java.util.List;

public interface ChatPort {
    ChatResult chat(ChatCommand command);

    record ChatCommand(String model, String prompt, boolean useTools, List<ChatMessage> conversationHistory) {
        public ChatCommand(String model, String prompt) {
            this(model, prompt, false, List.of());
        }
    }

    record ChatMessage(String role, String content) {}

    record ChatResult(String model, String text, List<String> toolsUsed) {
        public ChatResult(String model, String text) {
            this(model, text, List.of());
        }
    }
}
