package com.bko.fitnessextractor.ai.web;

import com.bko.fitnessextractor.ai.application.ChatService;
import com.bko.fitnessextractor.ai.application.ModelCatalogService;
import com.bko.fitnessextractor.ai.domain.ChatMessage;
import com.bko.fitnessextractor.ai.domain.ChatRequest;
import com.bko.fitnessextractor.ai.domain.ChatResponse;
import com.bko.fitnessextractor.ai.domain.FitnessContext;
import com.bko.fitnessextractor.ai.domain.FitnessContextPort;
import com.bko.fitnessextractor.ai.web.dto.ChatMessageDto;
import com.bko.fitnessextractor.ai.web.dto.ChatRequestDto;
import com.bko.fitnessextractor.ai.web.dto.ChatResponseDto;
import com.bko.fitnessextractor.ai.web.dto.FitnessContextDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiController {
    private final ChatService chatService;
    private final FitnessContextPort fitnessContextPort;
    private final ModelCatalogService modelCatalogService;

    public AiController(ChatService chatService, FitnessContextPort fitnessContextPort, ModelCatalogService modelCatalogService) {
        this.chatService = chatService;
        this.fitnessContextPort = fitnessContextPort;
        this.modelCatalogService = modelCatalogService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponseDto chat(@RequestBody ChatRequestDto requestDto) {
        ChatRequest request = new ChatRequest(
                toMessages(requestDto.messages()),
                requestDto.includeContext() != null && requestDto.includeContext(),
                requestDto.model()
        );
        ChatResponse response = chatService.chat(request);
        return new ChatResponseDto(response.model(), response.text(), response.usedFitnessContext());
    }

    @GetMapping(value = "/context", produces = MediaType.APPLICATION_JSON_VALUE)
    public FitnessContextDto context() {
        FitnessContext context = fitnessContextPort.loadContext();
        return new FitnessContextDto(context.messages(), context.strava(), context.garmin(), context.recovery());
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
