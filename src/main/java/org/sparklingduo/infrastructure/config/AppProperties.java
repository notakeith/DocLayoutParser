package org.sparklingduo.infrastructure.config;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Ocr ocr = new Ocr();
    private Llm llm = new Llm();
    private Image image = new Image();

    @Data
    public static class Ocr {
        private String provider;
        private String tessdataPath;
        private String language;
        private int pageSegMode;
        private String paddleUrl;
        private YandexConfig yandex = new YandexConfig();
    }

    @Data
    public static class YandexConfig {
        private String apiKey;
        private String folderId;
        private String iamToken;
    }

    @Data
    public static class Llm {
        private boolean enabled;
        private String apiKey;
        private String folderId;
        private String modelId;
    }

    @Data
    public static class Image {
        private int targetWidth;
    }
}