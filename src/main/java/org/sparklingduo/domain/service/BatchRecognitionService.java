package org.sparklingduo.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sparklingduo.domain.document.DocumentSource;
import org.sparklingduo.domain.exception.TemplateNotFoundException;
import org.sparklingduo.domain.job.JobStatus;
import org.sparklingduo.domain.job.RecognitionJob;
import org.sparklingduo.domain.template.Template;
import org.sparklingduo.infrastructure.pdf.PdfPageExtractor;
import org.sparklingduo.repository.RecognitionJobRepository;
import org.sparklingduo.repository.TemplateRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchRecognitionService {

    private final TemplateRepository templateRepository;
    private final RecognitionJobRepository jobRepository;
    private final RecognitionService recognitionService;
    private final PdfPageExtractor pdfPageExtractor;

    @Transactional
    public UUID submit(byte[] fileBytes, String fileName, UUID templateId) {
        RecognitionJob job = createJob(templateId, fileName);
        UUID jobId = job.getId();
        processAsync(jobId, fileBytes, fileName, templateId);
        return jobId;
    }

    @Transactional
    public List<UUID> submitBatch(List<byte[]> files, List<String> fileNames, UUID templateId) {
        validateTemplate(templateId);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            RecognitionJob job = createJob(templateId, fileNames.get(i));
            ids.add(job.getId());
            processAsync(job.getId(), files.get(i), fileNames.get(i), templateId);
        }
        log.info("Batch: создано {} jobs для шаблона {}", ids.size(), templateId);
        return ids;
    }

    @Async("recognitionExecutor")
    protected void processAsync(UUID jobId, byte[] fileBytes, String fileName, UUID templateId) {
        log.info("[job={}] Начало обработки файла '{}'", jobId, fileName);
        try {
            DocumentSource source = DocumentSource.from(fileBytes, fileName, pdfPageExtractor);
            recognitionService.recognizeForJob(source, templateId, jobId);
        } catch (Exception e) {
            log.error("[job={}] Обработка завершилась ошибкой: {}", jobId, e.getMessage());
        }
    }

    private RecognitionJob createJob(UUID templateId, String fileName) {
        Template template = validateTemplate(templateId);
        RecognitionJob job = RecognitionJob.builder()
                .template(template)
                .sourceFileName(fileName)
                .status(JobStatus.PENDING)
                .build();
        return jobRepository.save(job);
    }

    private Template validateTemplate(UUID templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId.toString()));
    }
}