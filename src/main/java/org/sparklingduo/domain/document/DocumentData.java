package org.sparklingduo.domain.document;

import java.util.List;

public record DocumentData (
        String templateName,
        List<FieldValue> fields
){}
