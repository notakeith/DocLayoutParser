package org.sparklingduo.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.template.FieldType;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class LlmCorrectionService {

    private final AppProperties appProperties;
    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://ai.api.cloud.yandex.net/v1")
            .build();

    public String correctText(String rawText, FieldType fieldType, String fieldName) {
        if (rawText == null || rawText.isBlank()) {
            return rawText;
        }
        if (!FieldTypePromptProvider.requiresCorrection(fieldType)) {
            return rawText;
        }

        String enrichedInput = FieldTypePromptProvider.buildInput(rawText, fieldType, fieldName);
        return callLlm(enrichedInput, rawText);
    }

    public String correctText(String rawText) {
        return correctText(rawText, FieldType.TEXT, "");
    }

    private String callLlm(String input, String fallback) {
        try {
            String apiKey  = appProperties.getLlm().getApiKey();
            String projectId = "b1gfc00pbbsgssjpk1o2";
            String promptId  = appProperties.getLlm().getModelId();

            Map<String, Object> body = Map.of(
                    "prompt", Map.of("id", promptId),
                    "input", input
            );

            JsonNode response = restClient.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Api-Key " + apiKey)
                    .header("OpenAI-Project", projectId)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) return fallback;

            JsonNode output = response.path("output");
            if (output.isArray() && !output.isEmpty()) {
                JsonNode content = output.get(0).path("content");
                if (content.isArray() && !content.isEmpty()) {
                    String result = content.get(0).path("text").asText();
                    if (result != null && !result.isBlank()) {
                        return result.strip();
                    }
                }
            }

            log.warn("LLM вернула пустую структуру");
            return fallback;

        } catch (Exception e) {
            log.error("LLM correction failed: {}", e.getMessage());
            return fallback;
        }
    }
}