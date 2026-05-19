package org.sparklingduo.infrastructure.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.port.OcrProvider;
import org.sparklingduo.domain.template.FieldType;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class YandexOcrService implements OcrProvider {
    private final AppProperties appProperties;
    private final RestClient restClient = RestClient.create();

    @Override
    public String extractText(byte[] imageContent, FieldType type) {
        String base64Image = Base64.getEncoder().encodeToString(imageContent);

        Map<String, Object> request = Map.of(
                "mimeType", "JPEG",
                "languageCodes", List.of("ru"),
                "model", "handwritten",
                "content", base64Image
        );

        try {
            var response = restClient.post()
                    .uri("https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText")
                    .header("Authorization", "Bearer " + appProperties.getOcr().getYandex().getIamToken())
                    .header("x-folder-id", appProperties.getOcr().getYandex().getFolderId())
                    .header("x-data-logging-enabled", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            return response.path("result").path("textAnnotation").path("fullText").asText();
        } catch (Exception e) {
            log.error("Yandex OCR failed: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public boolean supports(String providerName) {
        return "YANDEX".equalsIgnoreCase(providerName);
    }
}