package org.sparklingduo.infrastructure.ocr;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.port.OcrProvider;
import org.sparklingduo.domain.template.FieldType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class PaddleOcrService implements OcrProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.ocr.paddle-url}")
    private String paddleUrl;

    @Override
    public String extractText(byte[] imageContent, FieldType type) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            ByteArrayResource resource = new ByteArrayResource(imageContent) {
                @Override
                public String getFilename() { return "crop.jpg"; }
            };

            body.add("file", resource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(paddleUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("text");
            }
        } catch (Exception e) {
            log.error("PaddleOCR Error: {}", e.getMessage());
        }
        return "";
    }
}