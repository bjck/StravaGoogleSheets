package com.bko.fitnessextractor.ai.application;

import com.bko.fitnessextractor.ai.domain.ChatMessage;
import com.bko.fitnessextractor.ai.domain.ChatPort;
import com.bko.fitnessextractor.ai.domain.ChatRequest;
import com.bko.fitnessextractor.ai.domain.ChatResponse;
import com.bko.fitnessextractor.ai.domain.FitnessContext;
import com.bko.fitnessextractor.ai.domain.FitnessContextPort;
import com.bko.fitnessextractor.ai.infrastructure.gemini.GeminiProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final ChatPort chatPort;
    private final FitnessContextPort fitnessContextPort;
    private final GeminiProperties properties;

    public ChatService(ChatPort chatPort, FitnessContextPort fitnessContextPort, GeminiProperties properties) {
        this.chatPort = chatPort;
        this.fitnessContextPort = fitnessContextPort;
        this.properties = properties;
    }

    public ChatResponse chat(ChatRequest request) {
        String model = (request.model() == null || request.model().isBlank())
                ? properties.defaultModel()
                : request.model();
        // Guard against malformed model strings coming from clients.
        if (model.contains("{") || model.contains("}")) {
            model = properties.defaultModel();
        }
        // Ensure we send the short name, the Google client handles the prefix.
        if (model.startsWith("models/")) {
            model = model.substring("models/".length());
        }

        FitnessContext context = null;
        if (request.includeFitnessContext()) {
            context = fitnessContextPort.loadContext();
        }

        String prompt = buildPrompt(request.messages(), context);
        ChatPort.ChatResult result = chatPort.chat(new ChatPort.ChatCommand(model, prompt));
        return new ChatResponse(result.model(), result.text(), context != null && context.hasData());
    }

    private String buildPrompt(List<ChatMessage> messages, FitnessContext context) {
        StringBuilder prompt = new StringBuilder();
        if (context != null && context.hasData()) {
            prompt.append("User fitness context:\n").append(context.toPromptString()).append("\n\n");
        }
        if (messages != null && !messages.isEmpty()) {
            String rendered = messages.stream()
                    .map(m -> m.role().name().toLowerCase() + ": " + m.content())
                    .collect(Collectors.joining("\n"));
            prompt.append(rendered);
        } else {
            prompt.append("You are a fitness assistant. Provide concise answers.");
        }
        return prompt.toString();
    }
}
