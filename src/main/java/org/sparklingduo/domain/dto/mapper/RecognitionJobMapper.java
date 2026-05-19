package org.sparklingduo.domain.dto.mapper;

import org.sparklingduo.domain.dto.response.RecognitionJobDto;
import org.sparklingduo.domain.dto.response.RecognitionResultDto;
import org.sparklingduo.domain.job.RecognitionJob;
import org.sparklingduo.domain.job.RecognitionResult;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RecognitionJobMapper {

    public static RecognitionJobDto toDto(RecognitionJob job) {
        if (job == null) return null;

        List<RecognitionResult> rawResults;
        try {
            rawResults = job.getResults();
        } catch (Exception e) {
            rawResults = Collections.emptyList();
        }
        List<RecognitionResultDto> resultDtos = (rawResults != null)
                ? rawResults.stream().map(RecognitionJobMapper::toResultDto).collect(Collectors.toList())
                : Collections.emptyList();

        RecognitionJobDto.TemplateBasicDto templateDto = null;
        try {
            var tpl = job.getTemplate();
            if (tpl != null) {
                templateDto = RecognitionJobDto.TemplateBasicDto.builder()
                        .id(tpl.getId())
                        .name(tpl.getName())
                        .baseWidth(tpl.getBaseWidth())
                        .baseHeight(tpl.getBaseHeight())
                        .build();
            }
        } catch (Exception ignored) { }

        return RecognitionJobDto.builder()
                .id(job.getId())
                .template(templateDto)
                .status(job.getStatus())
                .sourceFileName(job.getSourceFileName())
                .sourceStorageKey(job.getSourceStorageKey())
                .createdAt(job.getCreatedAt())
                .finishedAt(job.getFinishedAt())
                .errorMessage(job.getErrorMessage())
                .results(resultDtos)
                .build();
    }

    private static RecognitionResultDto toResultDto(RecognitionResult r) {
        if (r == null) return null;
        return RecognitionResultDto.builder()
                .id(r.getId())
                .fieldName(r.getFieldName())
                .fieldType(r.getFieldType())
                .textValue(r.getTextValue())
                .imageStorageKey(r.getImageStorageKey())
                .build();
    }

    public static List<RecognitionJobDto> toDtoList(List<RecognitionJob> jobs) {
        if (jobs == null) return Collections.emptyList();
        return jobs.stream().map(RecognitionJobMapper::toDto).collect(Collectors.toList());
    }
}
