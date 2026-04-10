package org.sparklingduo.domain.service;

import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.dto.request.TemplateCreateDto;
import org.sparklingduo.domain.mapper.TemplateMapper;
import org.sparklingduo.domain.template.Template;
import org.sparklingduo.repository.TemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository repository;
    private final TemplateMapper mapper;

    @Transactional
    public Template createTemplate(TemplateCreateDto dto) {
        Template template = mapper.toEntity(dto);
        return repository.save(template);
    }

    public List<Template> getAllTemplates() {
        return repository.findAll();
    }

    public Template getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
    }

    public void deleteTemplate(UUID id) {
        repository.deleteById(id);
    }
}