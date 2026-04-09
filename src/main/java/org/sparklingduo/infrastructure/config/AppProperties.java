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
        // Путь к папке с обученными данными (rus.traineddata)
        private String tessdataPath;
        // Язык (по умолчанию rus)
        private String language = "rus";
    }

    @Data
    public static class Image {
        // Ширина, к которой приводим все документы перед кропом
        private int targetWidth = 2000;
    }
}