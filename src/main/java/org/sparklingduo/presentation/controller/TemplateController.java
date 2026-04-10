package org.sparklingduo.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.dto.request.TemplateCreateDto;
import org.sparklingduo.domain.service.TemplateService;
import org.sparklingduo.domain.template.Template;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templateService;

    @PostMapping
    public Template create(@RequestBody TemplateCreateDto dto) {
        return templateService.createTemplate(dto);
    }

    @GetMapping
    public List<Template> list() {
        return templateService.getAllTemplates();
    }

    @DeleteMapping(value = "/delete/{id}")
    public void delete(@PathVariable UUID id) {
        templateService.deleteTemplate(id);
    }
}