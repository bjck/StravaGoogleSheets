package com.bko.fitnessextractor.ai.web;

import com.bko.fitnessextractor.ai.application.ChatService;
import com.bko.fitnessextractor.ai.domain.ChatMessage;
import com.bko.fitnessextractor.ai.domain.ChatRequest;
import com.bko.fitnessextractor.ai.domain.FitnessContextPort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Minimal MCP-style endpoint exposing tools the AI can call for retrieval.
 * This is intentionally simple (HTTP + JSON) so it can be wired to a gateway or agent runner.
 */
@RestController
@RequestMapping("/mcp")
public class McpController {
    private final FitnessContextPort fitnessContextPort;
    private final ChatService chatService;

    public McpController(FitnessContextPort fitnessContextPort, ChatService chatService) {
        this.fitnessContextPort = fitnessContextPort;
        this.chatService = chatService;
    }

    @GetMapping(value = "/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, String>> listTools() {
        return List.of(
                Map.of("name", "fitness_summary", "description", "Return a compact summary of Strava + Garmin data"),
                Map.of("name", "ask_gemini", "description", "Send a prompt to Gemini with optional fitness context")
        );
    }

    @PostMapping(value = "/tools/fitness_summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object fitnessSummary() {
        return fitnessContextPort.loadContext();
    }

    @PostMapping(value = "/tools/ask_gemini", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object askGemini(@RequestBody Map<String, Object> body) {
        String prompt = body.getOrDefault("prompt", "").toString();
        boolean includeContext = Boolean.parseBoolean(body.getOrDefault("includeContext", "true").toString());
        String model = body.getOrDefault("model", "").toString();

        var response = chatService.chat(new ChatRequest(
                List.of(new ChatMessage(ChatMessage.Role.USER, prompt)),
                includeContext,
                model
        ));
        return Map.of(
                "model", response.model(),
                "text", response.text(),
                "usedContext", response.usedFitnessContext()
        );
    }
}
