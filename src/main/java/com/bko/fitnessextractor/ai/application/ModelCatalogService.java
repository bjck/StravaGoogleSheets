package com.bko.fitnessextractor.ai.application;

import com.bko.fitnessextractor.ai.infrastructure.gemini.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class ModelCatalogService {
    private static final Logger logger = LoggerFactory.getLogger(ModelCatalogService.class);

    private final WebClient webClient;
    private final GeminiProperties properties;

    public ModelCatalogService(GeminiProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    public List<ModelOption> listChatModels() {
        List<ModelOption> names = new ArrayList<>();
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            logger.warn("Gemini API key missing; cannot list models.");
            return names;
        }
        try {
            String url = "/models?key=" + properties.apiKey() + "&pageSize=200";
            ModelListResponse response = webClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(ModelListResponse.class)
                    .block();
            if (response != null && response.models != null) {
                response.models.stream()
                        .filter(m -> m.supportedGenerationMethods != null && m.supportedGenerationMethods.contains("generateContent"))
                        .map(m -> new ModelOption(
                                stripPrefix(m.name),
                                m.displayName != null ? m.displayName : stripPrefix(m.name),
                                m.name))
                        .forEach(names::add);
            }
        } catch (Exception e) {
            logger.warn("Failed to list Gemini models: {}", e.getMessage());
        }
        return names;
    }

    private String stripPrefix(String fullName) {
        if (fullName == null) return "";
        return fullName.startsWith("models/") ? fullName.substring("models/".length()) : fullName;
    }

    public record ModelOption(String name, String displayName, String fullName) {}
    private record ModelListResponse(List<ModelInfo> models, String nextPageToken) {}
    private record ModelInfo(String name, String displayName, List<String> supportedGenerationMethods) {}
}
