package org.sparklingduo.domain.dto.response;

import org.sparklingduo.domain.template.FieldType;

public record EntryDto(
        String name,
        BoxDto box,
        FieldType type,
        int padding
) {}
