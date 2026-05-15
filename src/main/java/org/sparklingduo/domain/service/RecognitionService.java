package org.sparklingduo.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.document.DocumentData;
import org.sparklingduo.domain.document.DocumentImage;
import org.sparklingduo.domain.document.FieldValue;
import org.sparklingduo.domain.exception.TemplateNotFoundException;
import org.sparklingduo.domain.port.ImageProcessor;
import org.sparklingduo.domain.port.OcrProvider;
import org.sparklingduo.domain.template.Box;
import org.sparklingduo.domain.template.Entry;
import org.sparklingduo.domain.template.FieldType;
import org.sparklingduo.domain.template.Template;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.sparklingduo.infrastructure.image.OpenCvImageProcessor;
import org.sparklingduo.infrastructure.llm.LlmCorrectionService;
import org.sparklingduo.infrastructure.ocr.TesseractOcrService;
import org.sparklingduo.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecognitionService {
    private final TemplateRepository templateRepository;
    private final ImageProcessor imageProcessor;
    private final List<OcrProvider> ocrProviders;
    private final LlmCorrectionService llmService;
    private final AppProperties appProperties;

    // Добавили InterruptedException в сигнатуру
    public DocumentData recognize(DocumentImage image, UUID templateId) throws URISyntaxException, InterruptedException {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId.toString()));

        byte[] preparedImage = imageProcessor.prepare(image.content());
        var currentSize = imageProcessor.getSize(preparedImage);

        double scaleX = (double) currentSize.width() / template.getBaseWidth();
        double scaleY = (double) currentSize.height() / template.getBaseHeight();

        OcrProvider ocrProvider = ocrProviders.stream()
                .filter(p -> p.supports(appProperties.getOcr().getProvider()))
                .findFirst()
                .orElse(ocrProviders.get(0));

        List<FieldValue> results = new ArrayList<>();

        for (Entry entry : template.getEntries()) {
            Box scaledBox = scaleBox(entry.getBox(), scaleX, scaleY, entry.getPadding());
            byte[] crop = imageProcessor.crop(preparedImage, scaledBox);

            if (entry.getType() == FieldType.SIGNATURE) {
                results.add(FieldValue.ofSignature(entry.getName(), crop));
            } else {
                // 1. Получаем сырой текст
                String text = ocrProvider.extractText(crop, entry.getType());

                // Ждем 1 секунду после OCR, так как у Яндекса лимит 1 RPS

                // 2. Прогоняем через LLM только если текст НЕ пустой
                if (appProperties.getLlm().isEnabled() && text != null && !text.isBlank()) {
                    log.info("Отправка в LLM поля: {}", entry.getName());
                    text = llmService.correctText(text);
                }

                Thread.sleep(1000);

                results.add(FieldValue.ofTextual(entry.getName(), text, entry.getType()));
            }
        }
        return new DocumentData(template.getName(), results);
    }

    private Box scaleBox(Box original, double sx, double sy, int padding) {
        return new Box(
                (int) (original.getX() * sx) - padding,
                (int) (original.getY() * sy) - padding,
                (int) (original.getWidth() * sx) + (padding * 2),
                (int) (original.getHeight() * sy) + (padding * 2),
                original.getPageNumber()
        );
    }
}
