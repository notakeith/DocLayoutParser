package org.sparklingduo.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sparklingduo.domain.job.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionJobDto {

    private UUID id;
    private TemplateBasicDto template;
    private JobStatus status;
    private String sourceFileName;
    private String sourceStorageKey;
    private Instant createdAt;
    private Instant finishedAt;
    private String errorMessage;

    private List<RecognitionResultDto> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateBasicDto {
        private UUID id;
        private String name;
        private Integer baseWidth;
        private Integer baseHeight;
    }
}
