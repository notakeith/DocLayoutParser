package org.sparklingduo.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.dto.mapper.RecognitionJobMapper;
import org.sparklingduo.domain.dto.response.RecognitionJobDto;
import org.sparklingduo.domain.port.ObjectStorage;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.sparklingduo.repository.RecognitionJobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "История и статусы задач распознавания")
public class RecognitionJobController {

    private final RecognitionJobRepository jobRepository;
    private final ObjectStorage objectStorage;
    private final AppProperties appProperties;

    @GetMapping
    @Operation(summary = "Все задачи (последние сверху)")
    @Transactional(readOnly = true)
    public List<RecognitionJobDto> listAll() {
        return RecognitionJobMapper.toDtoList(
                jobRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/by-template/{templateId}")
    @Operation(summary = "Задачи по шаблону")
    @Transactional(readOnly = true)
    public List<RecognitionJobDto> byTemplate(@PathVariable UUID templateId) {
        return RecognitionJobMapper.toDtoList(
                jobRepository.findByTemplateIdOrderByCreatedAtDesc(templateId));
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Статус и результаты конкретной задачи")
    @Transactional(readOnly = true)
    public ResponseEntity<RecognitionJobDto> get(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(RecognitionJobMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{jobId}/results/{resultId}/signature-url")
    @Operation(summary = "Временная ссылка на изображение подписи")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> signatureUrl(
            @PathVariable UUID jobId,
            @PathVariable UUID resultId
    ) {
        return jobRepository.findById(jobId)
                .flatMap(job -> job.getResults().stream()
                        .filter(r -> r.getId().equals(resultId))
                        .filter(r -> r.getImageStorageKey() != null)
                        .findFirst())
                .map(result -> {
                    String url = objectStorage.presignedGetUrl(
                            appProperties.getStorage().getReferenceBucket(),
                            result.getImageStorageKey(),
                            300
                    );
                    return ResponseEntity.ok(Map.of("url", url));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
