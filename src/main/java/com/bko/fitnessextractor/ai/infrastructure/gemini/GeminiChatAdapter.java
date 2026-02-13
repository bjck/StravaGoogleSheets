package com.bko.fitnessextractor.ai.infrastructure.gemini;

import com.bko.fitnessextractor.ai.domain.ChatPort;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.stereotype.Component;

@Component
public class GeminiChatAdapter implements ChatPort {
    private final Client client;

    public GeminiChatAdapter(Client client) {
        this.client = client;
    }

    @Override
    public ChatResult chat(ChatCommand command) {
        String model = command.model();
        GenerateContentResponse response = client.models.generateContent(model, command.prompt(), null);
        String text = response != null ? response.text() : "";
        return new ChatResult(model, text);
    }
}
