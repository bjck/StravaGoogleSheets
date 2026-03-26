package com.bko.fitnessextractor.ai.infrastructure.gemini;

import com.bko.fitnessextractor.ai.domain.ChatPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiChatAdapter implements ChatPort {
    private static final Logger logger = LoggerFactory.getLogger(GeminiChatAdapter.class);
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Client client;
    private final FitnessToolExecutor toolExecutor;

    public GeminiChatAdapter(Client client, FitnessToolExecutor toolExecutor) {
        this.client = client;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public ChatResult chat(ChatCommand command) {
        String model = command.model();
        boolean useTools = command.useTools();

        if (!useTools) {
            GenerateContentResponse response = client.models.generateContent(model, command.prompt(), null);
            String text = response != null ? response.text() : "";
            return new ChatResult(model, text, List.of());
        }

        return chatWithTools(model, command.prompt(), command.conversationHistory());
    }

    private ChatResult chatWithTools(String model, String prompt, List<ChatPort.ChatMessage> history) {
        Tool fitnessTool = FitnessToolDefinitions.buildTool();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .setTools(List.of(fitnessTool))
                .setSystemInstruction(Content.builder()
                        .setParts(List.of(Part.builder()
                                .setText(buildSystemPrompt())
                                .build()))
                        .build())
                .build();

        List<Content> contents = buildContents(history, prompt);
        List<String> toolsUsed = new ArrayList<>();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            GenerateContentResponse response = client.models.generateContent(model, contents, config);
            if (response == null) return new ChatResult(model, "No response from model.", toolsUsed);

            List<FunctionCall> functionCalls = extractFunctionCalls(response);
            if (functionCalls.isEmpty()) {
                String text = response.text() != null ? response.text() : "";
                return new ChatResult(model, text, toolsUsed);
            }

            // Add the model's response (with function calls) to the conversation
            Content modelContent = response.candidates().get(0).content();
            contents.add(modelContent);

            // Execute each function call and build response parts
            List<Part> responseParts = new ArrayList<>();
            for (FunctionCall fc : functionCalls) {
                String fnName = fc.name();
                Map<String, Object> args = toMap(fc.args());
                logger.info("Tool call: {}({})", fnName, args);
                toolsUsed.add(fnName);

                Map<String, Object> result = toolExecutor.execute(fnName, args);

                responseParts.add(Part.builder()
                        .setFunctionResponse(FunctionResponse.builder()
                                .setName(fnName)
                                .setResponse(toMapOfStringObject(result))
                                .build())
                        .build());
            }

            contents.add(Content.builder()
                    .setRole("user")
                    .setParts(responseParts)
                    .build());
        }

        return new ChatResult(model, "Reached maximum tool call rounds.", toolsUsed);
    }

    private String buildSystemPrompt() {
        return """
                You are a knowledgeable fitness coach and data analyst. You have access to the user's \
                workout data from Strava and health metrics from Garmin through function calls.

                When the user asks about their workouts, training, health, or fitness:
                1. First call list_activity_types or get_workout_overview to understand what data is available.
                2. Then use specific tool calls to retrieve the relevant data.
                3. Analyze the data and provide insightful, actionable advice.

                Be concise but thorough. Use specific numbers from the data. If you spot trends \
                (improving pace, declining sleep quality, etc.), mention them. Format your response \
                with markdown for readability.

                If the user asks something unrelated to fitness, answer normally without calling tools.""";
    }

    private List<Content> buildContents(List<ChatPort.ChatMessage> history, String currentPrompt) {
        List<Content> contents = new ArrayList<>();
        if (history != null) {
            for (ChatPort.ChatMessage msg : history) {
                String role = msg.role().equalsIgnoreCase("assistant") ? "model" : "user";
                contents.add(Content.builder()
                        .setRole(role)
                        .setParts(List.of(Part.builder().setText(msg.content()).build()))
                        .build());
            }
        }
        contents.add(Content.builder()
                .setRole("user")
                .setParts(List.of(Part.builder().setText(currentPrompt).build()))
                .build());
        return contents;
    }

    private List<FunctionCall> extractFunctionCalls(GenerateContentResponse response) {
        List<FunctionCall> calls = new ArrayList<>();
        if (response.candidates() == null || response.candidates().isEmpty()) return calls;
        var content = response.candidates().get(0).content();
        if (content == null || content.parts() == null) return calls;
        for (Part part : content.parts()) {
            if (part.functionCall() != null) {
                calls.add(part.functionCall());
            }
        }
        return calls;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object args) {
        if (args == null) return new HashMap<>();
        if (args instanceof Map) return (Map<String, Object>) args;
        try {
            return objectMapper.convertValue(args, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMapOfStringObject(Map<String, Object> input) {
        // The SDK expects Map<String, Object> for FunctionResponse.response
        return input != null ? input : new HashMap<>();
    }
}
