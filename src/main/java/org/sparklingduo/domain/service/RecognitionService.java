package org.sparklingduo.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.document.DocumentImage;
import org.sparklingduo.domain.document.DocumentSource;
import org.sparklingduo.domain.document.FieldValue;
import org.sparklingduo.domain.exception.TemplateNotFoundException;
import org.sparklingduo.domain.job.JobResult;
import org.sparklingduo.domain.job.RecognitionJob;
import org.sparklingduo.domain.job.RecognitionResult;
import org.sparklingduo.domain.port.DocumentAligner;
import org.sparklingduo.domain.port.ImageProcessor;
import org.sparklingduo.domain.port.ObjectStorage;
import org.sparklingduo.domain.port.OcrProvider;
import org.sparklingduo.domain.template.*;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.sparklingduo.infrastructure.llm.LlmCorrectionService;
import org.sparklingduo.infrastructure.pdf.PdfPageExtractor;
import org.sparklingduo.repository.RecognitionJobRepository;
import org.sparklingduo.repository.TemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecognitionService {

    private final TemplateRepository templateRepository;
    private final RecognitionJobRepository jobRepository;
    private final ImageProcessor imageProcessor;
    private final DocumentAligner documentAligner;
    private final ObjectStorage objectStorage;
    private final PdfPageExtractor pdfPageExtractor;
    private final List<OcrProvider> ocrProviders;
    private final LlmCorrectionService llmService;
    private final AppProperties appProperties;

    @Transactional
    public void recognizeForJob(DocumentSource source, UUID templateId, UUID jobId)
            throws URISyntaxException, InterruptedException {

        RecognitionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job не найден: " + jobId));

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId.toString()));

        job.markProcessing();
        jobRepository.save(job);

        try {
            uploadSourceIfNeeded(job, source);
            JobResult result = runPipeline(source, template, jobId);
            job.markDone(result.persistableResults());
            jobRepository.save(job);
            log.info("Job {} завершён: {} полей, {} страниц",
                    jobId, result.fields().size(), source.getPageCount());
        } catch (Exception e) {
            log.error("Job {} упал: {}", jobId, e.getMessage(), e);
            job.markFailed(e.getMessage());
            jobRepository.save(job);
            throw e;
        }
    }

    private JobResult runPipeline(DocumentSource source, Template template, UUID jobId)
            throws URISyntaxException, InterruptedException {

        OcrProvider ocrProvider = resolveOcrProvider();
        List<FieldValue> fields = new ArrayList<>();
        List<RecognitionResult> persistable = new ArrayList<>();

        for (int pageIdx = 0; pageIdx < source.getPageCount(); pageIdx++) {
            DocumentImage pageImage = source.getPages().get(pageIdx);
            final int pg = pageIdx;

            List<Entry> pageEntries = template.getEntries().stream()
                    .filter(e -> e.getBox().getPageNumber() == pg)
                    .toList();

            if (pageEntries.isEmpty()) {
                log.debug("Страница {} — нет полей, пропускаем", pg);
                continue;
            }

            byte[] working = alignPage(imageProcessor.prepare(pageImage.content()), template, pg);

            var size = imageProcessor.getSize(working);
            double scaleX = (double) size.width() / template.getBaseWidth();
            double scaleY = (double) size.height() / template.getBaseHeight();

            for (Entry entry : pageEntries) {
                if (entry.getType() == FieldType.ANCHOR) continue;

                Box scaledBox = scaleBox(entry.getBox(), scaleX, scaleY, entry.getPadding());
                byte[] crop = imageProcessor.crop(working, scaledBox);

                if (entry.getType() == FieldType.SIGNATURE) {
                    String key = uploadSignature(jobId, entry.getName(), crop);
                    fields.add(FieldValue.ofSignature(entry.getName(), crop));
                    persistable.add(RecognitionResult.ofSignature(entry.getName(), key));
                } else {
                    String text = extractWithLlm(ocrProvider, crop, entry.getType(), entry.getName());
                    fields.add(FieldValue.ofTextual(entry.getName(), text, entry.getType()));
                    persistable.add(RecognitionResult.ofText(entry.getName(), entry.getType(), text));
                }
            }
        }

        return JobResult.builder().fields(fields).persistableResults(persistable).build();
    }

    private byte[] alignPage(byte[] prepared, Template template, int pageIndex) {
        TemplatePage page = template.getPage(pageIndex);
        if (page == null) return prepared;
        try {
            byte[] reference = objectStorage.get(
                    appProperties.getStorage().getReferenceBucket(), page.getStorageKey());
            return documentAligner.align(prepared, reference);
        } catch (Exception e) {
            log.warn("Alignment стр.{} не удался: {}. Используем оригинал.", pageIndex, e.getMessage());
            return prepared;
        }
    }

    private String extractWithLlm(OcrProvider provider, byte[] crop, FieldType type, String fieldName)
            throws URISyntaxException, InterruptedException {
        String text = provider.extractText(crop, type);
        if (appProperties.getLlm().isEnabled() && text != null && !text.isBlank()) {
            text = llmService.correctText(text, type, fieldName);
        }
        Thread.sleep(1000);
        return text;
    }

    private OcrProvider resolveOcrProvider() {
        String name = appProperties.getOcr().getProvider();
        return ocrProviders.stream().filter(p -> p.supports(name)).findFirst()
                .orElseGet(() -> ocrProviders.get(0));
    }

    private void uploadSourceIfNeeded(RecognitionJob job, DocumentSource source) {
        if (source.getPageCount() == 0) return;
        byte[] first = source.getPages().get(0).content();
        String key = "jobs/%s/source.jpg".formatted(job.getId());
        objectStorage.put(appProperties.getStorage().getUploadsBucket(), key, first, "image/jpeg");
        job.setSourceStorageKey(key);
    }

    private String uploadSignature(UUID jobId, String fieldName, byte[] crop) {
        String key = "signatures/%s/%s.jpg".formatted(jobId, fieldName);
        objectStorage.put(appProperties.getStorage().getReferenceBucket(), key, crop, "image/jpeg");
        return key;
    }

    private Box scaleBox(Box original, double sx, double sy, int padding) {
        return new Box(
                (int) (original.getX() * sx) - padding,
                (int) (original.getY() * sy) - padding,
                (int) (original.getWidth() * sx) + (padding * 2),
                (int) (original.getHeight() * sy) + (padding * 2),
                original.getPageNumber());
    }
}