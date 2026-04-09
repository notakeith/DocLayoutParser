package org.sparklingduo.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.document.DocumentData;
import org.sparklingduo.domain.document.DocumentImage;
import org.sparklingduo.domain.document.FieldValue;
import org.sparklingduo.domain.port.ImageProcessor;
import org.sparklingduo.domain.port.OcrProvider;
import org.sparklingduo.domain.template.Box;
import org.sparklingduo.domain.template.Entry;
import org.sparklingduo.domain.template.FieldType;
import org.sparklingduo.domain.template.Template;
import org.sparklingduo.infrastructure.image.OpenCvImageProcessor;
import org.sparklingduo.infrastructure.ocr.TesseractOcrService;
import org.sparklingduo.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecognitionService {

    private final ImageProcessor imageProcessor;
    private final OcrProvider ocrProvider;
    private final TemplateRepository templateRepository;

    public DocumentData recognize(DocumentImage image, UUID templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        byte[] preparedImage = imageProcessor.prepare(image.content());

        ImageProcessor.ImageSize currentSize = imageProcessor.getSize(preparedImage);

        double scaleX = (double) currentSize.width() / template.getBaseWidth();
        double scaleY = (double) currentSize.height() / template.getBaseHeight();

        List<FieldValue> results = new ArrayList<>();

        for (Entry entry : template.getEntries()) {
            Box scaledBox = scaleBox(entry.getBox(), scaleX, scaleY, entry.getPadding());

            byte[] crop = imageProcessor.crop(preparedImage, scaledBox);

            if (entry.getType() == FieldType.SIGNATURE) {
                results.add(FieldValue.ofSignature(entry.getName(), crop));
            } else {
                String text = ocrProvider.extractText(crop, entry.getType());
                results.add(FieldValue.ofTextual(entry.getName(), text, entry.getType()));
            }
        }

        return new DocumentData(template.getName(), results);
    }

    private Box scaleBox(Box original, double sx, double sy, int padding) {
        return new Box(
                (int) (original.x() * sx) - padding,
                (int) (original.y() * sy) - padding,
                (int) (original.width() * sx) + (padding * 2),
                (int) (original.height() * sy) + (padding * 2),
                original.pageNumber()
        );
    }
}