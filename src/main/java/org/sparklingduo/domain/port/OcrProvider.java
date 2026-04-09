package org.sparklingduo.domain.port;

import org.sparklingduo.domain.template.FieldType;

import java.net.URISyntaxException;

public interface OcrProvider {
    String extractText(byte[] imageContent, FieldType type) throws URISyntaxException;
}
