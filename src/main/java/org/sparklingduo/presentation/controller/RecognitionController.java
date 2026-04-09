package org.sparklingduo.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.document.DocumentData;
import org.sparklingduo.domain.document.DocumentImage;
import org.sparklingduo.domain.document.ImageFormat;
import org.sparklingduo.domain.service.RecognitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;

@RestController
@RequestMapping("/api/recognition")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

    @PostMapping("/process")
    public ResponseEntity<DocumentData> processDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("templateId") UUID templateId) throws IOException, URISyntaxException {

        DocumentImage image = new DocumentImage(
                file.getBytes(),
                ImageFormat.JPEG,
                file.getOriginalFilename()
        );

        DocumentData result = recognitionService.recognize(image, templateId);

        return ResponseEntity.ok(result);
    }
}