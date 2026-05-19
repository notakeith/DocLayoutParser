package org.sparklingduo.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.dto.request.TemplateCreateDto;
import org.sparklingduo.domain.port.ObjectStorage;
import org.sparklingduo.domain.service.TemplateService;
import org.sparklingduo.domain.template.Template;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.sparklingduo.repository.TemplatePageRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final TemplatePageRepository templatePageRepository;
    private final ObjectStorage objectStorage;
    private final AppProperties appProperties;

    @PostMapping(value = "/with-reference", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Template createWithReference(
            @RequestPart("dto") TemplateCreateDto dto,
            @RequestPart("referenceFile") MultipartFile referenceFile
    ) throws IOException {
        return templateService.createTemplate(dto, referenceFile.getBytes(), referenceFile.getOriginalFilename());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Template create(@RequestBody TemplateCreateDto dto) {
        return templateService.createTemplate(dto);
    }

    @GetMapping
    public List<Template> list() {
        return templateService.getAllTemplates();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Template> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @GetMapping("/{id}/pages")
    public ResponseEntity<?> getPages(@PathVariable UUID id) {
        var pages = templatePageRepository.findByTemplateIdOrderByPageNumberAsc(id);
        return ResponseEntity.ok(pages);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Template> patch(
            @PathVariable UUID id,
            @RequestBody TemplateCreateDto dto
    ) {
        return ResponseEntity.ok(templateService.updateEntries(id, dto));
    }

    @PostMapping("/{id}/discard")
    public ResponseEntity<Void> discard(@PathVariable UUID id) {
        try {
            templateService.deleteTemplate(id);
        } catch (Exception ignored) { }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pages/{pageId}/image")
    public ResponseEntity<byte[]> getPageImage(@PathVariable UUID id, @PathVariable UUID pageId) {
        return templatePageRepository.findById(pageId)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(objectStorage.get(
                                appProperties.getStorage().getReferenceBucket(),
                                p.getStorageKey()
                        )))
                .orElse(ResponseEntity.notFound().build());
    }
}