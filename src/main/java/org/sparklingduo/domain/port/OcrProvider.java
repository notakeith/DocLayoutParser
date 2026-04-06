package org.sparklingduo.domain.port;

import org.sparklingduo.domain.template.FieldType;

public interface OcrProvider {
    String extractText(byte[] imageContent, FieldType type);
}
