package org.sparklingduo.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.document.DocumentData;
import org.sparklingduo.domain.document.DocumentImage;
import org.sparklingduo.domain.document.ImageFormat;
import org.sparklingduo.domain.service.RecognitionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;

@RestController
@RequestMapping("/api/recognition")
@RequiredArgsConstructor
@Tag(name = "Recognition", description = "Методы для распознавания документов")
public class RecognitionController {

    private final RecognitionService recognitionService;

    @Operation(summary = "Распознать документ по шаблону",
            description = "Загрузите изображение и укажите ID шаблона для извлечения данных")
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentData process(
            @Parameter(
                    description = "Файл изображения (jpg/png)",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            )
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "UUID существующего шаблона")
            @RequestParam("templateId") UUID templateId
    ) throws IOException, URISyntaxException, InterruptedException {

        DocumentImage image = new DocumentImage(
                file.getBytes(),
                ImageFormat.JPEG,
                file.getOriginalFilename()
        );

        return recognitionService.recognize(image, templateId);
    }
}