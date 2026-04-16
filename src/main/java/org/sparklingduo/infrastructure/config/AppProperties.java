package org.sparklingduo.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Ocr ocr = new Ocr();
    private Image image = new Image();

    @Data
    public static class Ocr {
        private String tessdataPath;
        private String language = "rus";
        private Integer pageSegMode = 7;
        private String paddleUrl;
    }

    @Data
    public static class Image {
        private int targetWidth = 2000;
    }
}