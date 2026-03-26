package com.bko.fitnessextractor.ai.web;

import com.bko.fitnessextractor.ai.application.ChatService;
import com.bko.fitnessextractor.ai.application.ModelCatalogService;
import com.bko.fitnessextractor.ai.domain.ChatMessage;
import com.bko.fitnessextractor.ai.domain.ChatRequest;
import com.bko.fitnessextractor.ai.domain.ChatResponse;
import com.bko.fitnessextractor.ai.web.dto.ChatMessageDto;
import com.bko.fitnessextractor.ai.web.dto.ChatRequestDto;
import com.bko.fitnessextractor.ai.web.dto.ChatResponseDto;
import com.bko.fitnessextractor.workout.WorkoutStorePort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {
    private final ChatService chatService;
    private final ModelCatalogService modelCatalogService;
    private final WorkoutStorePort workoutStore;

    public AiController(ChatService chatService, ModelCatalogService modelCatalogService,
                        WorkoutStorePort workoutStore) {
        this.chatService = chatService;
        this.modelCatalogService = modelCatalogService;
        this.workoutStore = workoutStore;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponseDto chat(@RequestBody ChatRequestDto requestDto) {
        ChatRequest request = new ChatRequest(
                toMessages(requestDto.messages()),
                requestDto.includeContext() != null && requestDto.includeContext(),
                requestDto.model()
        );
        ChatResponse response = chatService.chat(request);
        return new ChatResponseDto(response.model(), response.text(), response.usedFitnessContext(), response.toolsUsed());
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalWorkouts", workoutStore.getWorkoutCount());
        stats.put("activityTypes", workoutStore.getActivityTypes());
        var earliest = workoutStore.getEarliestWorkoutDate();
        var latest = workoutStore.getLatestWorkoutDate();
        stats.put("earliestDate", earliest != null ? earliest.toString() : null);
        stats.put("latestDate", latest != null ? latest.toString() : null);
        stats.put("summaryByType", workoutStore.getWorkoutSummaryByType().stream()
                .map(row -> {
                    Map<String, Object> ts = new LinkedHashMap<>();
                    ts.put("type", row[0]);
                    ts.put("count", row[1]);
                    Double totalDist = (Double) row[2];
                    if (totalDist != null) ts.put("totalDistanceKm", Math.round(totalDist / 100.0) / 10.0);
                    Long totalSec = row[3] != null ? ((Number) row[3]).longValue() : null;
                    if (totalSec != null) ts.put("totalHours", Math.round(totalSec / 36.0) / 100.0);
                    return ts;
                }).toList());
        var recentGarmin = workoutStore.getRecentGarminData(7);
        if (!recentGarmin.isEmpty()) {
            var latest7 = recentGarmin.get(0);
            Map<String, Object> garmin = new LinkedHashMap<>();
            garmin.put("date", latest7.getDate().toString());
            garmin.put("bodyBatteryMax", latest7.getBodyBatteryMax());
            garmin.put("restingHR", latest7.getRestingHeartRate());
            garmin.put("vo2Max", latest7.getVo2Max());
            garmin.put("sleepScore", latest7.getSleepScore());
            garmin.put("weight", latest7.getWeight());
            stats.put("latestGarmin", garmin);
        }
        return stats;
    }

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ModelCatalogService.ModelOption> models() {
        return modelCatalogService.listChatModels();
    }

    private List<ChatMessage> toMessages(List<ChatMessageDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream()
                .map(dto -> new ChatMessage(
                        parseRole(dto.role()),
                        dto.content() == null ? "" : dto.content()))
                .toList();
    }

    private ChatMessage.Role parseRole(String role) {
        if (role == null) return ChatMessage.Role.USER;
        return switch (role.toLowerCase()) {
            case "system" -> ChatMessage.Role.SYSTEM;
            default -> ChatMessage.Role.USER;
        };
    }
}
