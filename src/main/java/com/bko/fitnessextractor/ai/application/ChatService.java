package com.bko.fitnessextractor.ai.application;

import com.bko.fitnessextractor.ai.domain.ChatPort;
import com.bko.fitnessextractor.ai.domain.ChatRequest;
import com.bko.fitnessextractor.ai.domain.ChatResponse;
import com.bko.fitnessextractor.ai.infrastructure.gemini.GeminiProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {
    private final ChatPort chatPort;
    private final GeminiProperties properties;

    public ChatService(ChatPort chatPort, GeminiProperties properties) {
        this.chatPort = chatPort;
        this.properties = properties;
    }

    public ChatResponse chat(ChatRequest request) {
        String model = resolveModel(request.model());

        List<ChatPort.ChatMessage> history = null;
        if (request.messages() != null) {
            history = request.messages().stream()
                    .map(m -> new ChatPort.ChatMessage(m.role().name().toLowerCase(), m.content()))
                    .toList();
        }

        String prompt = request.messages() != null && !request.messages().isEmpty()
                ? request.messages().get(request.messages().size() - 1).content()
                : "Hello";

        // Remove the last message from history since it's the current prompt
        if (history != null && !history.isEmpty()) {
            history = history.subList(0, history.size() - 1);
        }

        boolean useTools = request.includeFitnessContext();
        ChatPort.ChatCommand command = new ChatPort.ChatCommand(model, prompt, useTools, history);
        ChatPort.ChatResult result = chatPort.chat(command);

        return new ChatResponse(result.model(), result.text(), !result.toolsUsed().isEmpty(), result.toolsUsed());
    }

    private String resolveModel(String model) {
        if (model == null || model.isBlank()) {
            model = properties.defaultModel();
        }
        if (model.contains("{") || model.contains("}")) {
            model = properties.defaultModel();
        }
        if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }
        return model;
    }
}
