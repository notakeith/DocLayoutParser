package org.sparklingduo.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sparklingduo.domain.template.FieldType;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionResultDto {
    private UUID id;
    private String fieldName;
    private FieldType fieldType;
    private String textValue;
    private String imageStorageKey;
    private Double confidence;
}
