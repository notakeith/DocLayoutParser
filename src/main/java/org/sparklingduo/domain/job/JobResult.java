package org.sparklingduo.domain.job;

import lombok.Builder;
import org.sparklingduo.domain.document.FieldValue;

import java.util.List;

@Builder
public record JobResult(
        List<FieldValue> fields,
        List<RecognitionResult> persistableResults
) {}