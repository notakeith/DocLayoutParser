package org.sparklingduo.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YandexResponse(
        @JsonProperty("output_text")
        String outputText
) {
}