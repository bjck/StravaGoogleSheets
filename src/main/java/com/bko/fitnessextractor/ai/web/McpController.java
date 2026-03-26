package com.bko.fitnessextractor.ai.web;

import com.bko.fitnessextractor.ai.application.ChatService;
import com.bko.fitnessextractor.ai.domain.ChatMessage;
import com.bko.fitnessextractor.ai.domain.ChatRequest;
import com.bko.fitnessextractor.ai.infrastructure.gemini.FitnessToolExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpController {
    private final FitnessToolExecutor toolExecutor;
    private final ChatService chatService;

    public McpController(FitnessToolExecutor toolExecutor, ChatService chatService) {
        this.toolExecutor = toolExecutor;
        this.chatService = chatService;
    }

    @GetMapping(value = "/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, String>> listTools() {
        return List.of(
                Map.of("name", "list_activity_types", "description", "List all distinct activity types"),
                Map.of("name", "get_workout_overview", "description", "High-level workout overview with stats"),
                Map.of("name", "get_recent_workouts", "description", "Get the most recent workouts"),
                Map.of("name", "get_garmin_summary", "description", "Recent Garmin health metrics"),
                Map.of("name", "ask_gemini", "description", "Send a prompt to Gemini with tool-calling for fitness data")
        );
    }

    @PostMapping(value = "/tools/{toolName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object executeTool(@PathVariable String toolName, @RequestBody(required = false) Map<String, Object> body) {
        if ("ask_gemini".equals(toolName)) {
            return askGemini(body);
        }
        return toolExecutor.execute(toolName, body);
    }

    private Object askGemini(Map<String, Object> body) {
        String prompt = body != null ? body.getOrDefault("prompt", "").toString() : "";
        String model = body != null ? body.getOrDefault("model", "").toString() : "";

        var response = chatService.chat(new ChatRequest(
                List.of(new ChatMessage(ChatMessage.Role.USER, prompt)),
                true,
                model
        ));
        return Map.of(
                "model", response.model(),
                "text", response.text(),
                "usedContext", response.usedFitnessContext(),
                "toolsUsed", response.toolsUsed()
        );
    }
}
