package org.sparklingduo.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.dto.mapper.RecognitionJobMapper;
import org.sparklingduo.domain.dto.response.RecognitionJobDto;
import org.sparklingduo.domain.job.RecognitionJob;
import org.sparklingduo.domain.service.BatchRecognitionService;
import org.sparklingduo.repository.RecognitionJobRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/recognition")
@RequiredArgsConstructor
@Tag(name = "Recognition")
public class RecognitionController {

    private final BatchRecognitionService batchService;
    private final RecognitionJobRepository jobRepository;

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Отправить документ на распознавание")
    public Map<String, UUID> submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam("templateId") UUID templateId
    ) throws IOException {
        UUID jobId = batchService.submit(file.getBytes(), file.getOriginalFilename(), templateId);
        return Map.of("jobId", jobId);
    }

    @PostMapping(value = "/submit-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Пакетная отправка документов")
    public Map<String, List<UUID>> submitBatch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("templateId") UUID templateId
    ) throws IOException {
        List<byte[]> bytes = new java.util.ArrayList<>();
        List<String> names = new java.util.ArrayList<>();
        for (MultipartFile f : files) {
            bytes.add(f.getBytes());
            names.add(f.getOriginalFilename());
        }
        List<UUID> jobIds = batchService.submitBatch(bytes, names, templateId);
        return Map.of("jobIds", jobIds);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Статус и результаты job")
    @Transactional(readOnly = true)
    public ResponseEntity<RecognitionJobDto> getJob(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(RecognitionJobMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Deprecated
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "[deprecated] Синхронное распознавание")
    public ResponseEntity<Map<String, UUID>> processLegacy(
            @RequestPart("file") MultipartFile file,
            @RequestParam("templateId") UUID templateId
    ) throws IOException {
        UUID jobId = batchService.submit(file.getBytes(), file.getOriginalFilename(), templateId);
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            RecognitionJob job = jobRepository.findById(jobId).orElseThrow();
            switch (job.getStatus()) {
                case DONE -> { return ResponseEntity.ok(Map.of("jobId", jobId)); }
                case FAILED -> { return ResponseEntity.internalServerError().body(Map.of("jobId", jobId)); }
                default -> {
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
        }
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }
}