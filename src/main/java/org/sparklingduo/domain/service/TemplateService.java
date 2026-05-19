package org.sparklingduo.domain.service;

import lombok.RequiredArgsConstructor;
import org.sparklingduo.domain.dto.request.TemplateCreateDto;
import org.sparklingduo.domain.mapper.TemplateMapper;
import org.sparklingduo.domain.port.ObjectStorage;
import org.sparklingduo.domain.template.Template;
import org.sparklingduo.domain.template.TemplatePage;
import org.sparklingduo.infrastructure.config.AppProperties;
import org.sparklingduo.infrastructure.pdf.PdfPageExtractor;
import org.sparklingduo.repository.RecognitionJobRepository;
import org.sparklingduo.repository.TemplatePageRepository;
import org.sparklingduo.repository.TemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository repository;
    private final TemplatePageRepository pageRepository;
    private final RecognitionJobRepository jobRepository;
    private final TemplateMapper mapper;
    private final ObjectStorage objectStorage;
    private final PdfPageExtractor pdfPageExtractor;
    private final AppProperties appProperties;

    @Transactional
    public Template createTemplate(TemplateCreateDto dto) {
        return repository.save(mapper.toEntity(dto));
    }

    @Transactional
    public Template createTemplate(TemplateCreateDto dto, byte[] referenceBytes, String fileName) {
        // 1. Сохраняем шаблон без страниц
        Template template = repository.save(mapper.toEntity(dto));

        // 2. Конвертируем файл в страницы и загружаем в MinIO
        List<byte[]> pageImages = extractPages(referenceBytes, fileName);
        List<TemplatePage> pages = new ArrayList<>();
        for (int i = 0; i < pageImages.size(); i++) {
            String key = "templates/%s/page_%d.jpg".formatted(template.getId(), i);
            objectStorage.put(
                    appProperties.getStorage().getReferenceBucket(),
                    key, pageImages.get(i), "image/jpeg"
            );
            pages.add(TemplatePage.builder()
                    .pageNumber(i)
                    .storageKey(key)
                    .width(dto.getBaseWidth())
                    .height(dto.getBaseHeight())
                    .build());
        }

        // 3. Пересохраняем шаблон со страницами и обновлённым pageCount.
        //    Страницы добавляем в коллекцию — Hibernate сам проставит template_id
        //    через @JoinColumn(name = "template_id") в Template.pages.
        Template updated = Template.builder()
                .id(template.getId())
                .name(template.getName())
                .baseWidth(template.getBaseWidth())
                .baseHeight(template.getBaseHeight())
                .pageCount(pageImages.size())
                .entries(template.getEntries())
                .pages(pages)
                .build();
        return repository.save(updated);
    }

    public List<Template> getAllTemplates() {
        return repository.findAll();
    }

    public Template getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        // Сначала удаляем все связанные jobs (FK constraint)
        jobRepository.deleteByTemplateId(id);

        pageRepository.findByTemplateIdOrderByPageNumberAsc(id)
                .forEach(p -> objectStorage.delete(
                        appProperties.getStorage().getReferenceBucket(),
                        p.getStorageKey()
                ));
        repository.deleteById(id);
    }

    /**
     * Обновить name и entries существующего шаблона.
     * Используется после разметки PDF: страницы уже сохранены, обновляем только поля.
     */
    @Transactional
    public Template updateEntries(UUID id, TemplateCreateDto dto) {
        Template existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));

        // Перестраиваем entries через mapper
        List<org.sparklingduo.domain.template.Entry> newEntries = dto.getEntries() == null
                ? new java.util.ArrayList<>()
                : dto.getEntries().stream().map(mapper::toEntry).toList();

        Template updated = Template.builder()
                .id(existing.getId())
                .name(dto.getName() != null ? dto.getName() : existing.getName())
                .baseWidth(existing.getBaseWidth())
                .baseHeight(existing.getBaseHeight())
                .pageCount(existing.getPageCount())
                .entries(new java.util.ArrayList<>(newEntries))
                .pages(existing.getPages())
                .build();
        return repository.save(updated);
    }

    private List<byte[]> extractPages(byte[] bytes, String fileName) {
        boolean isPdf = (fileName != null && fileName.toLowerCase().endsWith(".pdf"))
                || (bytes.length >= 4
                && bytes[0] == 0x25 && bytes[1] == 0x50
                && bytes[2] == 0x44 && bytes[3] == 0x46);
        return isPdf ? pdfPageExtractor.extractPages(bytes) : List.of(bytes);
    }
}